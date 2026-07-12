package com.prognimak.jobscanner.scanner;

import com.prognimak.jobscanner.config.JobScannerProperties;
import com.prognimak.jobscanner.entity.ContractType;
import com.prognimak.jobscanner.entity.JobOffer;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import com.prognimak.jobscanner.entity.RemoteType;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GlassdoorScanner implements JobSourceScanner {

    private static final Logger log = LoggerFactory.getLogger(GlassdoorScanner.class);

    private final RestClient restClient;
    private final JobScannerProperties.Glassdoor properties;

    public GlassdoorScanner(RestClient.Builder restClientBuilder, JobScannerProperties properties) {
        this.properties = properties.getScanners().getGlassdoor();
        this.restClient = restClientBuilder
                .defaultHeader("User-Agent", this.properties.getUserAgent())
                .defaultHeader("Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,"
                                + "image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .defaultHeader("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
                .defaultHeader("Cache-Control", "no-cache")
                .defaultHeader("Pragma", "no-cache")
                .defaultHeader("Referer", this.properties.getBaseUrl() + "/Job/index.htm")
                .defaultHeader("Sec-Ch-Ua", "\"Chromium\";v=\"126\", \"Google Chrome\";v=\"126\", \"Not-A.Brand\";v=\"99\"")
                .defaultHeader("Sec-Ch-Ua-Mobile", "?0")
                .defaultHeader("Sec-Ch-Ua-Platform", "\"macOS\"")
                .defaultHeader("Sec-Fetch-Dest", "document")
                .defaultHeader("Sec-Fetch-Mode", "navigate")
                .defaultHeader("Sec-Fetch-Site", "same-origin")
                .defaultHeader("Sec-Fetch-User", "?1")
                .defaultHeader("Upgrade-Insecure-Requests", "1")
                .build();
    }

    @Override
    public String source() {
        return "glassdoor";
    }

    @Override
    public List<JobOffer> scan(JobSearchCriteria criteria) {
        if (!properties.isEnabled()) {
            return List.of();
        }

        long startedAt = System.nanoTime();
        List<JobOffer> candidates = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        for (int page = 1; page <= Math.max(1, properties.getMaxPages()); page++) {
            for (String searchUrl : buildSearchUrls(criteria, page)) {
                try {
                    String html = fetch(searchUrl);
                    for (JobOffer offer : extractOffersFromSearchPage(html, searchUrl, criteria)) {
                        if (seenUrls.add(offer.getJobUrl())) {
                            candidates.add(offer);
                        }
                        if (candidates.size() >= Math.max(1, properties.getMaxCandidates())) {
                            break;
                        }
                    }
                    delay();
                } catch (RuntimeException ex) {
                    if (isBlockedByGlassdoor(ex)) {
                        log.warn("Glassdoor blocked the search request with its security page. url={}", searchUrl);
                        throw blockedException(ex);
                    }
                    if (isForbiddenByGlassdoor(ex)) {
                        log.warn("Glassdoor returned 403 Forbidden for the search request. url={}", searchUrl);
                        throw blockedException(ex);
                    }
                    log.warn("Glassdoor search request failed for {}: {}", searchUrl, ex.getMessage());
                }
                if (candidates.size() >= Math.max(1, properties.getMaxCandidates())) {
                    break;
                }
            }
            if (candidates.size() >= Math.max(1, properties.getMaxCandidates())) {
                break;
            }
        }

        List<JobOffer> offers = fetchDetailsInParallel(candidates, criteria);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("Glassdoor scan finished: candidates={}, imported={}, elapsedMs={}",
                candidates.size(), offers.size(), elapsedMs);
        return offers;
    }

    private List<JobOffer> fetchDetailsInParallel(List<JobOffer> candidates, JobSearchCriteria criteria) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        int maxDetails = Math.max(1, properties.getMaxDetails());
        Semaphore concurrencyLimit = new Semaphore(Math.max(1, properties.getMaxParallelDetailRequests()));
        List<JobOffer> offers = new ArrayList<>();
        List<Future<Optional<JobOffer>>> futures = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletionService<Optional<JobOffer>> completionService = new ExecutorCompletionService<>(executor);
            for (JobOffer candidate : candidates) {
                futures.add(completionService.submit(() -> fetchAndParseDetail(candidate, criteria, concurrencyLimit)));
            }

            for (int completed = 0; completed < futures.size() && offers.size() < maxDetails; completed++) {
                try {
                    completionService.take().get().ifPresent(offers::add);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException ex) {
                    log.warn("Glassdoor detail worker failed", ex);
                }
            }
            futures.forEach(future -> future.cancel(true));
        }

        return offers;
    }

    private Optional<JobOffer> fetchAndParseDetail(JobOffer cardOffer, JobSearchCriteria criteria,
                                                   Semaphore concurrencyLimit) {
        boolean acquired = false;
        try {
            concurrencyLimit.acquire();
            acquired = true;
            String html = fetch(cardOffer.getJobUrl());
            Optional<JobOffer> parsedOffer = parseDetail(html, cardOffer.getJobUrl(), criteria);
            if (parsedOffer.isEmpty()) {
                return matchesLocalCriteria(cardOffer, criteria) ? Optional.of(cardOffer) : Optional.empty();
            }
            JobOffer offer = parsedOffer.get();
            mergeCardFallbacks(offer, cardOffer);
            return Optional.of(offer);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException ex) {
            if (isBlockedByGlassdoor(ex)) {
                log.warn("Glassdoor blocked the detail request with its security page. Using search card fallback. url={}",
                        cardOffer.getJobUrl());
            } else if (isForbiddenByGlassdoor(ex)) {
                log.warn("Glassdoor returned 403 Forbidden for detail request. Using search card fallback. url={}",
                        cardOffer.getJobUrl());
            } else {
                log.warn("Glassdoor detail request failed for {}: {}", cardOffer.getJobUrl(), ex.getMessage());
            }
            return matchesLocalCriteria(cardOffer, criteria) ? Optional.of(cardOffer) : Optional.empty();
        } finally {
            if (acquired) {
                concurrencyLimit.release();
            }
        }
    }

    List<String> buildSearchUrls(JobSearchCriteria criteria, int page) {
        List<String> urls = new ArrayList<>();
        String seoUrl = buildSeoSearchUrl(criteria, page);
        if (!seoUrl.isBlank()) {
            urls.add(seoUrl);
        }
        String queryUrl = buildQuerySearchUrl(criteria, page);
        if (!urls.contains(queryUrl)) {
            urls.add(queryUrl);
        }
        return urls;
    }

    private String buildSeoSearchUrl(JobSearchCriteria criteria, int page) {
        if (criteria.getKeyword() == null || criteria.getKeyword().isBlank()
                || criteria.getLocation() != null && !criteria.getLocation().isBlank()) {
            return "";
        }
        String keyword = criteria.getKeyword().trim();
        String slug = slugify(keyword);
        if (slug.isBlank()) {
            return "";
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/Job/")
                .path(slug)
                .path("-jobs-SRCH_KO0,")
                .path(String.valueOf(keyword.length()))
                .path(".htm");
        if (page > 1) {
            builder.queryParam("p", page);
        }
        return builder.build().encode(StandardCharsets.UTF_8).toUriString();
    }

    private String buildQuerySearchUrl(JobSearchCriteria criteria, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path(properties.getSearchPath());

        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            builder.queryParam("sc.keyword", criteria.getKeyword());
        }
        if (criteria.getLocation() != null && !criteria.getLocation().isBlank()) {
            builder.queryParam("locKeyword", criteria.getLocation());
        }
        if (page > 1) {
            builder.queryParam("p", page);
        }
        return builder.build().encode(StandardCharsets.UTF_8).toUriString();
    }

    private String slugify(String value) {
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    private String fetch(String url) {
        return restClient.get()
                .uri(URI.create(url))
                .retrieve()
                .body(String.class);
    }

    private boolean isBlockedByGlassdoor(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof HttpClientErrorException.Forbidden forbidden) {
                String body = forbidden.getResponseBodyAsString().toLowerCase(Locale.ROOT);
                return body.contains("<title>security | glassdoor</title>")
                        || body.contains("security | glassdoor")
                        || body.contains("glassdoor");
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isForbiddenByGlassdoor(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof HttpClientErrorException.Forbidden) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ScannerBlockedException blockedException(Throwable cause) {
        return new ScannerBlockedException(source(),
                "Glassdoor blockiert Backend-Zugriffe mit HTTP 403. Es wurden keine Glassdoor-Jobs importiert.",
                cause);
    }

    List<JobOffer> extractOffersFromSearchPage(String html, String baseUrl, JobSearchCriteria criteria) {
        Document document = Jsoup.parse(html, baseUrl);
        List<JobOffer> offers = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();

        for (Element titleLink : document.select("a[href*=/job-listing/], a[href*=/partner/jobListing.htm]")) {
            String jobUrl = normalizeUrl(titleLink.absUrl("href"));
            if (!isJobDetailUrl(jobUrl) || !seenUrls.add(jobUrl)) {
                continue;
            }
            Element card = nearestCard(titleLink);
            String title = cleanTitle(titleLink.text());
            if (title.isBlank()) {
                title = firstText(card, "[data-test*=job-title]", "[class*=jobTitle]", "h2", "h3");
            }
            if (isGenericTitle(title)) {
                continue;
            }

            JobOffer offer = new JobOffer();
            offer.setSource(source());
            offer.setTitle(title);
            offer.setCompany(extractCompany(card, title));
            offer.setLocation(extractLocation(card, title));
            offer.setRemoteType(detectRemoteType(card.text()));
            offer.setContractType(ContractType.PERMANENT);
            offer.setDescription(extractCardDescription(card, title));
            offer.setJobUrl(jobUrl);
            offer.setDetectedAt(Instant.now());
            offer.setRateOrSalary(extractRate(card.text()));
            if (matchesLocalCriteria(offer, criteria)) {
                offers.add(offer);
            }
        }
        return offers;
    }

    Optional<JobOffer> parseDetail(String html, String detailUrl) {
        return parseDetail(html, detailUrl, null);
    }

    Optional<JobOffer> parseDetail(String html, String detailUrl, JobSearchCriteria criteria) {
        Document document = Jsoup.parse(html, detailUrl);
        String title = cleanTitle(firstText(document,
                "[data-test=job-title]",
                "[data-test*=job-title]",
                ".JobDetails_jobTitle__Rw_gn",
                ".jobTitle",
                "main h1",
                "h1"));
        if (isGenericTitle(title)) {
            return Optional.empty();
        }

        JobOffer offer = new JobOffer();
        offer.setSource(source());
        offer.setTitle(title);
        offer.setCompany(firstText(document,
                "[data-test=employer-name]",
                "[data-test*=employer]",
                ".EmployerProfile_compactEmployerName__LE242",
                ".employerName"));
        offer.setLocation(firstText(document,
                "[data-test=location]",
                "[data-test*=location]",
                ".JobDetails_location__MbnUM",
                ".location"));
        offer.setRemoteType(detectRemoteType(document.text()));
        offer.setContractType(ContractType.PERMANENT);
        offer.setDescription(extractDescription(document));
        offer.setJobUrl(normalizeUrl(detailUrl));
        offer.setDetectedAt(Instant.now());
        offer.setRateOrSalary(extractRate(document.text()));

        if (!matchesLocalCriteria(offer, criteria)) {
            return Optional.empty();
        }
        return Optional.of(offer);
    }

    private Element nearestCard(Element titleLink) {
        Element current = titleLink;
        while (current != null && current.parent() != null) {
            String tag = current.tagName();
            String test = current.attr("data-test").toLowerCase(Locale.ROOT);
            String classes = current.className().toLowerCase(Locale.ROOT);
            if ("li".equals(tag)
                    || "article".equals(tag)
                    || test.contains("joblisting")
                    || test.contains("job-listing")
                    || classes.contains("jobcard")
                    || classes.contains("job-card")
                    || classes.contains("joblistitem")) {
                return current;
            }
            current = current.parent();
        }
        return titleLink.parent() == null ? titleLink : titleLink.parent();
    }

    private String extractCompany(Element card, String title) {
        String company = firstText(card,
                "[data-test=employer-name]",
                "[data-test*=employer]",
                "[class*=employerName]",
                "[class*=compactEmployerName]");
        if (!company.isBlank()) {
            return cleanCompany(company);
        }
        return cleanCompany(firstMeaningfulLineBeforeTitle(card.text(), title));
    }

    private String extractLocation(Element card, String title) {
        String location = firstText(card,
                "[data-test=location]",
                "[data-test*=location]",
                "[class*=location]",
                "[class*=jobLocation]");
        if (!location.isBlank()) {
            return cleanLocation(location);
        }
        String line = firstMeaningfulLineAfterTitle(card.text(), title);
        return cleanLocation(line);
    }

    private String firstMeaningfulLineBeforeTitle(String text, String title) {
        List<String> lines = splitLines(text);
        int titleIndex = findLineIndex(lines, title);
        for (int index = titleIndex - 1; index >= 0; index--) {
            String line = lines.get(index);
            if (isMeaningfulMetadataLine(line)) {
                return line;
            }
        }
        return "";
    }

    private String firstMeaningfulLineAfterTitle(String text, String title) {
        List<String> lines = splitLines(text);
        int titleIndex = findLineIndex(lines, title);
        for (int index = titleIndex + 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (isMeaningfulMetadataLine(line)) {
                return line;
            }
        }
        return "";
    }

    private List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R| {2,}")) {
            String trimmed = line.replaceAll("\\s+", " ").trim();
            if (!trimmed.isBlank()) {
                lines.add(trimmed);
            }
        }
        return lines;
    }

    private int findLineIndex(List<String> lines, String title) {
        String normalizedTitle = normalizeText(title);
        for (int index = 0; index < lines.size(); index++) {
            if (normalizeText(lines.get(index)).contains(normalizedTitle)) {
                return index;
            }
        }
        return Math.min(1, lines.size());
    }

    private boolean isMeaningfulMetadataLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return !lower.equals("anzeige")
                && !lower.equals("schnell bewerben")
                && !lower.matches("\\d+[Tt].*")
                && !lower.matches("[0-5],[0-9]")
                && !line.contains("€");
    }

    private String extractCardDescription(Element card, String title) {
        List<String> parts = new ArrayList<>();
        String company = extractCompany(card, title);
        String location = extractLocation(card, title);
        if (!company.isBlank()) {
            parts.add("Firma: " + company);
        }
        if (!location.isBlank()) {
            parts.add("Ort: " + location);
        }
        String text = card.text().replaceAll("\\s+", " ").trim();
        if (!text.isBlank()) {
            parts.add(trimTo(text, 2000));
        }
        return String.join("\n", parts);
    }

    private String extractDescription(Document document) {
        String description = firstText(document,
                "[data-test=jobDescriptionContent]",
                "[data-test*=jobDescription]",
                "#JobDescriptionContainer",
                ".JobDetails_jobDescription__uW_fK",
                ".jobDescriptionContent",
                "section:contains(Stellenbeschreibung)",
                "main");
        if (description.isBlank()) {
            description = document.body().text();
        }
        return trimTo(description, 12000);
    }

    private void mergeCardFallbacks(JobOffer offer, JobOffer cardOffer) {
        if (offer.getCompany() == null || offer.getCompany().isBlank()) {
            offer.setCompany(cardOffer.getCompany());
        }
        if (offer.getLocation() == null || offer.getLocation().isBlank()) {
            offer.setLocation(cardOffer.getLocation());
        }
        if (offer.getRemoteType() == null) {
            offer.setRemoteType(cardOffer.getRemoteType());
        }
        if (offer.getDescription() == null || offer.getDescription().isBlank()) {
            offer.setDescription(cardOffer.getDescription());
        }
        if (offer.getRateOrSalary() == null) {
            offer.setRateOrSalary(cardOffer.getRateOrSalary());
        }
    }

    private String firstText(Element root, String... selectors) {
        if (root == null) {
            return "";
        }
        for (String selector : selectors) {
            Element element = root.selectFirst(selector);
            if (element != null) {
                String text = element.text().replaceAll("\\s+", " ").trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private RemoteType detectRemoteType(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("hybrid")) {
            return RemoteType.HYBRID;
        }
        if (lower.contains("remote") || lower.contains("homeoffice") || lower.contains("home office")) {
            return RemoteType.REMOTE;
        }
        if (lower.contains("vor ort") || lower.contains("onsite") || lower.contains("on-site")) {
            return RemoteType.ON_SITE;
        }
        return null;
    }

    private BigDecimal extractRate(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d{2,3})[.]?\\d{0,3}\\s*€\\s*-\\s*(\\d{2,3})[.]?\\d{0,3}\\s*€",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (matcher.find()) {
            String value = matcher.group(2).replace(".", "");
            return new BigDecimal(value + "000");
        }
        return null;
    }

    private boolean matchesLocalCriteria(JobOffer offer, JobSearchCriteria criteria) {
        if (criteria == null) {
            return true;
        }
        if (criteria.getRemoteType() != null && offer.getRemoteType() != null
                && criteria.getRemoteType() != offer.getRemoteType()) {
            return false;
        }
        if (criteria.getContractType() != null && offer.getContractType() != null
                && criteria.getContractType() != offer.getContractType()) {
            return false;
        }
        if (criteria.getLocation() != null && !criteria.getLocation().isBlank()
                && offer.getLocation() != null && !offer.getLocation().isBlank()
                && !offer.getLocation().toLowerCase(Locale.ROOT)
                .contains(criteria.getLocation().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (criteria.getMinRate() != null && offer.getRateOrSalary() != null
                && offer.getRateOrSalary().compareTo(criteria.getMinRate()) < 0) {
            return false;
        }
        return matchesKeyword(offer, criteria);
    }

    private boolean matchesKeyword(JobOffer offer, JobSearchCriteria criteria) {
        if (criteria.getKeyword() == null || criteria.getKeyword().isBlank()) {
            return true;
        }
        String keyword = criteria.getKeyword().toLowerCase(Locale.ROOT).trim();
        String searchableText = (safeText(offer.getTitle()) + " " + safeText(offer.getDescription()))
                .toLowerCase(Locale.ROOT);
        if (keyword.contains("javascript")) {
            return searchableText.contains("javascript") || searchableText.contains("java script");
        }
        boolean searchesJava = java.util.regex.Pattern.compile("(^|[^a-z0-9])java([^a-z0-9]|$)")
                .matcher(keyword)
                .find();
        if (searchesJava) {
            return java.util.regex.Pattern.compile("(^|[^a-z0-9])java([^a-z0-9]|$)")
                    .matcher(searchableText)
                    .find();
        }
        for (String token : keyword.split("[,;\\s]+")) {
            if (!token.isBlank() && !searchableText.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private boolean isJobDetailUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith(properties.getBaseUrl().toLowerCase(Locale.ROOT))
                && (lower.contains("/job-listing/") || lower.contains("/partner/joblisting.htm"));
    }

    private String cleanTitle(String title) {
        return title == null ? "" : title.replace(" | Glassdoor", "").replace(" - Glassdoor", "").trim();
    }

    private String cleanCompany(String company) {
        return company == null ? "" : company.replaceAll("\\s+[0-5],[0-9]$", "").trim();
    }

    private String cleanLocation(String location) {
        return location == null ? "" : location.replaceAll("\\s+", " ").trim();
    }

    private boolean isGenericTitle(String title) {
        if (title == null || title.isBlank()) {
            return true;
        }
        String lower = title.toLowerCase(Locale.ROOT);
        return lower.contains("job suchen")
                || lower.contains("jobsuche")
                || lower.contains("deine jobsuche beginnt");
    }

    private String normalizeUrl(String url) {
        int fragmentStart = url.indexOf('#');
        String withoutFragment = fragmentStart >= 0 ? url.substring(0, fragmentStart) : url;
        return withoutFragment.trim();
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String trimTo(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private void delay() {
        if (properties.getRequestDelayMs() <= 0) {
            return;
        }
        try {
            Thread.sleep(properties.getRequestDelayMs());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
