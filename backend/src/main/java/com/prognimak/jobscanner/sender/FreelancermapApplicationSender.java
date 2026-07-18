package com.prognimak.jobscanner.sender;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitUntilState;
import com.prognimak.jobscanner.config.JobScannerProperties;
import com.prognimak.jobscanner.entity.ApplicationDraft;
import com.prognimak.jobscanner.entity.JobOffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "job-scanner.sender", name = "type", havingValue = "freelancermap")
public class FreelancermapApplicationSender implements ApplicationSender {

    private static final Logger log = LoggerFactory.getLogger(FreelancermapApplicationSender.class);

    private static final Pattern APPLY_ACTION = Pattern.compile(
            ".*(bewerben|jetzt\\s+bewerben|angebot\\s+abgeben|angebot\\s+senden|kontakt\\s+aufnehmen|anfragen|interesse|apply).*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SUBMIT_ACTION = Pattern.compile(
            ".*(bewerbung\\s+absenden|bewerbung\\s+senden|angebot\\s+senden|absenden|senden|bewerben|submit).*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COOKIE_ACTION = Pattern.compile(
            ".*(alle\\s+akzeptieren|akzeptieren|zustimmen|einverstanden|accept).*",
            Pattern.CASE_INSENSITIVE);

    private final JobScannerProperties.FreelancermapSender properties;

    public FreelancermapApplicationSender(JobScannerProperties properties) {
        this.properties = properties.getSender().getFreelancermap();
    }

    @Override
    public synchronized void send(ApplicationDraft draft) {
        JobOffer jobOffer = draft.getJobOffer();
        if (jobOffer == null || jobOffer.getSource() == null
                || !jobOffer.getSource().toLowerCase(Locale.ROOT).contains("freelancermap")) {
            throw new IllegalStateException("Freelancermap sender can only send freelancermap drafts.");
        }
        if (draft.getAnschreibenText() == null || draft.getAnschreibenText().isBlank()) {
            throw new IllegalStateException("Cannot send freelancermap application without Anschreiben text.");
        }

        try (Playwright playwright = Playwright.create();
             BrowserContext context = launchContext(playwright)) {
            Page page = context.pages().isEmpty() ? context.newPage() : context.pages().getFirst();
            page.setDefaultTimeout(timeoutMillis());
            page.setDefaultNavigationTimeout(timeoutMillis());

            navigate(page, jobOffer.getJobUrl());
            acceptCookies(page);
            openApplicationForm(page);
            fillAnschreiben(page, draft.getAnschreibenText());
            boolean cvSelected = selectExistingCv(page);
            if (!cvSelected) {
                log.warn("No existing CV checkbox/radio could be selected on freelancermap page for draft {}",
                        draft.getId());
            }
            acceptDataPrivacyIfPresent(page);
            submitApplication(page);
            log.info("Freelancermap application flow finished for draft {} and job {}",
                    draft.getId(), jobOffer.getId());
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Freelancermap application sending failed: " + ex.getMessage(), ex);
        }
    }

    private BrowserContext launchContext(Playwright playwright) {
        BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(properties.isHeadless())
                .setSlowMo(Math.max(0, properties.getSlowMoMs()))
                .setViewportSize(1440, 1000);
        if (hasText(properties.getBrowserChannel())) {
            options.setChannel(properties.getBrowserChannel());
        }
        return playwright.chromium().launchPersistentContext(Path.of(properties.getUserDataDir()), options);
    }

    private void navigate(Page page, String jobUrl) {
        if (!hasText(jobUrl)) {
            throw new IllegalStateException("Job URL is missing.");
        }
        page.navigate(jobUrl, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(timeoutMillis()));
    }

    private void acceptCookies(Page page) {
        clickFirst(page, List.of(
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(COOKIE_ACTION)),
                page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(COOKIE_ACTION))
        ), Duration.ofSeconds(3), false);
    }

    private void openApplicationForm(Page page) {
        if (hasApplicationTextArea(page, Duration.ofSeconds(2))) {
            return;
        }

        clickApplyAction(page);
        if (hasApplicationTextArea(page, Duration.ofSeconds(8))) {
            return;
        }

        if (looksLikeLoginPage(page)) {
            log.info("Freelancermap login is required. Complete login in the opened browser window.");
            waitForManualLogin(page);
            acceptCookies(page);
        }

        if (!hasApplicationTextArea(page, Duration.ofSeconds(3))) {
            clickApplyAction(page);
        }
        if (!hasApplicationTextArea(page, Duration.ofSeconds(12))) {
            throw new IllegalStateException("Could not find freelancermap application message textarea.");
        }
    }

    private void clickApplyAction(Page page) {
        boolean clicked = clickFirst(page, List.of(
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(APPLY_ACTION)),
                page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(APPLY_ACTION)),
                page.locator("a[href*='bewerb'], a[href*='apply'], button[data-testid*='apply'], button[class*='apply']")
        ), Duration.ofSeconds(10), false);
        if (!clicked && !looksLikeLoginPage(page)) {
            throw new IllegalStateException("Could not find freelancermap apply button/link.");
        }
        waitForPage(page);
    }

    private void fillAnschreiben(Page page, String anschreibenText) {
        for (Frame frame : page.frames()) {
            if (fillFirst(applicationEditorLocators(frame), anschreibenText)) {
                return;
            }
        }
        for (Frame frame : page.frames()) {
            if (fillEditorWithJavaScript(frame, anschreibenText)) {
                return;
            }
        }
        log.warn("Could not fill freelancermap Anschreiben field. url={}, frames={}, visibleEditors={}",
                page.url(), page.frames().size(), countVisibleApplicationEditors(page));
        throw new IllegalStateException("Could not fill freelancermap Anschreiben field.");
    }

    private boolean selectExistingCv(Page page) {
        Object selected = page.evaluate("""
                () => {
                  const terms = ['lebenslauf', 'cv', 'resume', 'vita', 'profil'];
                  const rejected = ['datenschutz', 'agb', 'privacy', 'terms', 'newsletter', 'einwilligung'];
                  const inputs = Array.from(document.querySelectorAll('input[type="checkbox"], input[type="radio"]'));
                  const isCvInput = (input) => {
                    const labels = [];
                    const wrappingLabel = input.closest('label');
                    if (wrappingLabel) labels.push(wrappingLabel.innerText || '');
                    if (input.id) {
                      const explicit = Array.from(document.querySelectorAll('label'))
                        .find(label => label.htmlFor === input.id);
                      if (explicit) labels.push(explicit.innerText || '');
                    }
                    labels.push(input.getAttribute('aria-label') || '');
                    labels.push(input.getAttribute('name') || '');
                    labels.push(input.getAttribute('value') || '');
                    const text = labels.join(' ').toLowerCase();
                    return terms.some(term => text.includes(term)) && !rejected.some(term => text.includes(term));
                  };
                  const existing = inputs.find(input => isCvInput(input) && input.checked);
                  if (existing) {
                    existing.scrollIntoView({ block: 'center', inline: 'center' });
                    return true;
                  }
                  for (const input of inputs) {
                    if (isCvInput(input)) {
                      input.scrollIntoView({ block: 'center', inline: 'center' });
                      input.click();
                      return true;
                    }
                  }
                  return false;
                }
                """);
        return Boolean.TRUE.equals(selected);
    }

    private void submitApplication(Page page) {
        if (!properties.isSubmitEnabled()) {
            throw new IllegalStateException("Freelancermap submit is disabled by configuration.");
        }
        boolean clicked = clickFirst(page, List.of(
                page.locator("button[data-id='contact-send-application']:visible"),
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT_ACTION)),
                page.locator("button[type='submit']"),
                page.locator("input[type='submit']")
        ), Duration.ofSeconds(10), false);
        if (!clicked) {
            throw new IllegalStateException("Could not find freelancermap final submit button.");
        }
        waitForPage(page);
        waitForSubmitResult(page);
    }

    private boolean clickFirst(Page page, List<Locator> locators, Duration timeout, boolean forceLast) {
        double timeoutMs = timeout.toMillis();
        for (Locator locator : locators) {
            try {
                int count = Math.min(locator.count(), 8);
                if (count == 0) {
                    continue;
                }
                Locator candidate = forceLast ? locator.nth(count - 1) : locator.first();
                candidate.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(timeoutMs));
                candidate.click(new Locator.ClickOptions().setTimeout(timeoutMs));
                return true;
            } catch (RuntimeException ignored) {
                // Try the next selector; freelancermap changes labels and markup over time.
            }
        }
        return false;
    }

    private void acceptDataPrivacyIfPresent(Page page) {
        boolean found = false;
        for (Frame frame : page.frames()) {
            String result = checkDataPrivacyInFrame(frame);
            if ("missing".equals(result)) {
                continue;
            }
            found = true;
            if ("checked".equals(result)) {
                return;
            }
            throw new IllegalStateException("Could not check freelancermap data privacy checkbox.");
        }
        if (!found) {
            log.info("No freelancermap data privacy checkbox found before submit.");
        }
    }

    private String checkDataPrivacyInFrame(Frame frame) {
        try {
            Object result = frame.evaluate("""
                    () => {
                      const input = document.querySelector(
                        'input#data-privacy[name="data-privacy"], input#data-privacy, input[name="data-privacy"]'
                      );
                      if (!input) {
                        return 'missing';
                      }
                      if (input.checked) {
                        return 'checked';
                      }
                      input.scrollIntoView({ block: 'center', inline: 'center' });
                      input.click();
                      if (!input.checked) {
                        input.checked = true;
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        input.dispatchEvent(new Event('change', { bubbles: true }));
                      }
                      return input.checked ? 'checked' : 'failed';
                    }
                    """);
            return String.valueOf(result);
        } catch (RuntimeException ignored) {
            return "failed";
        }
    }

    private boolean fillFirst(List<Locator> locators, String value) {
        for (Locator locator : locators) {
            try {
                int count = Math.min(locator.count(), 8);
                for (int index = 0; index < count; index++) {
                    Locator candidate = locator.nth(index);
                    if (!candidate.isVisible()) {
                        continue;
                    }
                    candidate.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions()
                            .setTimeout(Duration.ofSeconds(5).toMillis()));
                    candidate.click(new Locator.ClickOptions().setTimeout(Duration.ofSeconds(5).toMillis()));
                    candidate.fill(value, new Locator.FillOptions().setTimeout(Duration.ofSeconds(5).toMillis()));
                    return true;
                }
            } catch (RuntimeException ignored) {
                // Try the next selector.
            }
        }
        return false;
    }

    private boolean hasApplicationTextArea(Page page, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (countVisibleApplicationEditors(page) > 0) {
                return true;
            }
            page.waitForTimeout(250);
        }
        return false;
    }

    private List<Locator> applicationEditorLocators(Frame frame) {
        return List.of(
                frame.locator("#cover-letter:visible"),
                frame.locator("textarea#cover-letter:visible"),
                frame.locator("textarea:visible[name*='message' i]"),
                frame.locator("textarea:visible[name*='anschreiben' i]"),
                frame.locator("textarea:visible[name*='cover' i]"),
                frame.locator("textarea:visible[placeholder*='nachricht' i]"),
                frame.locator("textarea:visible[placeholder*='anschreiben' i]"),
                frame.locator("textarea:visible[placeholder*='bewerbung' i]"),
                frame.locator("[contenteditable]:visible"),
                frame.locator("[role='textbox']:visible"),
                frame.locator(".ql-editor:visible"),
                frame.locator(".ProseMirror:visible"),
                frame.locator("textarea:visible")
        );
    }

    private boolean fillEditorWithJavaScript(Frame frame, String value) {
        try {
            Object result = frame.evaluate("""
                    (text) => {
                      const isVisible = (element) => {
                        const style = window.getComputedStyle(element);
                        const rect = element.getBoundingClientRect();
                        return style
                          && style.visibility !== 'hidden'
                          && style.display !== 'none'
                          && rect.width > 0
                          && rect.height > 0;
                      };
                      const coverLetter = document.querySelector('#cover-letter');
                      const candidates = [
                        ...(coverLetter ? [coverLetter] : []),
                        ...Array.from(document.querySelectorAll(
                          'textarea, [contenteditable], [role="textbox"], .ql-editor, .ProseMirror'
                        ))
                      ].filter((element, index, elements) => elements.indexOf(element) === index)
                       .filter(isVisible);
                      for (const element of candidates) {
                        element.scrollIntoView({ block: 'center', inline: 'center' });
                        element.focus();
                        if ('value' in element) {
                          element.value = text;
                          element.dispatchEvent(new Event('input', { bubbles: true }));
                          element.dispatchEvent(new Event('change', { bubbles: true }));
                          return true;
                        }
                        element.textContent = text;
                        element.dispatchEvent(new InputEvent('input', {
                          bubbles: true,
                          inputType: 'insertText',
                          data: text
                        }));
                        element.dispatchEvent(new Event('change', { bubbles: true }));
                        return true;
                      }
                      return false;
                    }
                    """, value);
            return Boolean.TRUE.equals(result);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private int countVisibleApplicationEditors(Page page) {
        int count = 0;
        for (Frame frame : page.frames()) {
            try {
                count += frame.locator("""
                        #cover-letter:visible,
                        textarea:visible,
                        [contenteditable]:visible,
                        [role='textbox']:visible,
                        .ql-editor:visible,
                        .ProseMirror:visible
                        """).count();
            } catch (RuntimeException ignored) {
                // Cross-origin or transient frames can disappear while the page is changing.
            }
        }
        return count;
    }

    private boolean looksLikeLoginPage(Page page) {
        String url = page.url().toLowerCase(Locale.ROOT);
        if (url.contains("login") || url.contains("einloggen") || url.contains("anmelden")) {
            return true;
        }
        return page.locator("input[type='password']").count() > 0;
    }

    private void waitForManualLogin(Page page) {
        long deadline = System.nanoTime() + Duration.ofSeconds(
                Math.max(30, properties.getManualLoginTimeoutSeconds())).toNanos();
        while (System.nanoTime() < deadline) {
            if (!looksLikeLoginPage(page) || hasApplicationTextArea(page, Duration.ofSeconds(1))) {
                waitForPage(page);
                return;
            }
            page.waitForTimeout(1000);
        }
        throw new IllegalStateException("Manual freelancermap login was not completed in time.");
    }

    private void waitForSubmitResult(Page page) {
        try {
            page.getByText(Pattern.compile(
                    ".*(erfolgreich|gesendet|versendet|eingegangen|vielen\\s+dank|danke).*",
                    Pattern.CASE_INSENSITIVE))
                    .first()
                    .waitFor(new Locator.WaitForOptions().setTimeout(Duration.ofSeconds(20).toMillis()));
        } catch (TimeoutError ex) {
            log.info("No explicit freelancermap success message detected after submit; submit click completed.");
        }
    }

    private void waitForPage(Page page) {
        try {
            page.waitForLoadState();
        } catch (RuntimeException ignored) {
            // Single-page transitions do not always produce a load state.
        }
    }

    private double timeoutMillis() {
        return Duration.ofSeconds(Math.max(15, properties.getTimeoutSeconds())).toMillis();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
