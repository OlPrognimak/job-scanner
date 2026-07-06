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
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class FreelancermapScanner implements JobSourceScanner {

    private static final Logger log = LoggerFactory.getLogger(FreelancermapScanner.class);

    private final RestClient restClient;
    private final JobScannerProperties.Freelancermap properties;

    public FreelancermapScanner(RestClient.Builder restClientBuilder, JobScannerProperties properties) {
        this.properties = properties.getScanners().getFreelancermap();
        this.restClient = restClientBuilder
                .defaultHeader("User-Agent", this.properties.getUserAgent())
                .defaultHeader("Accept", "text/html,application/xhtml+xml")
                .build();
    }

    @Override
    public String source() {
        return "freelancermap";
    }

    @Override
    public List<JobOffer> scan(JobSearchCriteria criteria) {
        if (!properties.isEnabled()) {
            return List.of();
        }

        Set<String> detailUrls = new LinkedHashSet<>();
        for (int page = 1; page <= Math.max(1, properties.getMaxPages()); page++) {
            String searchUrl = buildSearchUrl(criteria, page);
            try {
                String html = fetch(searchUrl);
                detailUrls.addAll(extractDetailUrls(html, searchUrl));
                delay();
            } catch (RuntimeException ex) {
                log.warn("Freelancermap search request failed for {}", searchUrl, ex);
            }
        }

        List<JobOffer> offers = new ArrayList<>();
        for (String detailUrl : detailUrls.stream().limit(Math.max(1, properties.getMaxDetails())).toList()) {
            try {
                String html = fetch(detailUrl);
                parseDetail(html, detailUrl).ifPresent(offers::add);
                delay();
            } catch (RuntimeException ex) {
                log.warn("Freelancermap detail request failed for {}", detailUrl, ex);
            }
        }
        return offers;
    }

    private String buildSearchUrl(JobSearchCriteria criteria, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path(properties.getSearchPath());

        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            builder.queryParam("query", criteria.getKeyword());
        }
        if (criteria.getLocation() != null && !criteria.getLocation().isBlank()) {
            builder.queryParam("continents", "")
                    .queryParam("countries", "")
                    .queryParam("states", "")
                    .queryParam("city", criteria.getLocation());
        }
        if (page > 1) {
            builder.queryParam("pagenr", page);
        }
        return builder.build().encode(StandardCharsets.UTF_8).toUriString();
    }

    private String fetch(String url) {
        return restClient.get()
                .uri(URI.create(url))
                .retrieve()
                .body(String.class);
    }

    Set<String> extractDetailUrls(String html, String baseUrl) {
        Document document = Jsoup.parse(html, baseUrl);
        Set<String> urls = new LinkedHashSet<>();
        for (Element link : document.select("a[href]")) {
            String href = link.attr("href");
            String absoluteUrl = normalizeUrl(link.absUrl("href"));
            if (isProjectDetailUrl(href, absoluteUrl)) {
                urls.add(absoluteUrl);
            }
        }
        return urls;
    }

    java.util.Optional<JobOffer> parseDetail(String html, String detailUrl) {
        Document document = Jsoup.parse(html, detailUrl);
        String title = firstText(document,
                "h1",
                "[data-testid*=title]",
                ".project-title",
                ".project-header h2",
                "title");
        if (title.isBlank()) {
            return java.util.Optional.empty();
        }

        JobOffer offer = new JobOffer();
        offer.setSource(source());
        offer.setTitle(cleanTitle(title));
        offer.setCompany(firstText(document,
                "[data-testid*=company]",
                ".company",
                ".client",
                ".project-company",
                ".customer"));
        offer.setLocation(firstText(document,
                "[data-testid*=location]",
                ".location",
                ".project-location",
                "span:contains(Ort)",
                "li:contains(Ort)"));
        offer.setRemoteType(detectRemoteType(document.text()));
        offer.setContractType(ContractType.FREELANCE);
        offer.setDescription(extractDescription(document));
        offer.setJobUrl(normalizeUrl(detailUrl));
        offer.setDetectedAt(Instant.now());
        offer.setRateOrSalary(extractRate(document.text()));
        return java.util.Optional.of(offer);
    }

    private boolean isProjectDetailUrl(String href, String absoluteUrl) {
        if (absoluteUrl.isBlank() || absoluteUrl.contains("#")) {
            return false;
        }
        String lowerHref = href.toLowerCase(Locale.ROOT);
        String lowerUrl = absoluteUrl.toLowerCase(Locale.ROOT);
        return lowerUrl.startsWith(properties.getBaseUrl().toLowerCase(Locale.ROOT))
                && !lowerUrl.contains("projektboerse.html")
                && !lowerUrl.contains("/freelancer/")
                && (lowerHref.contains("/projekt/")
                || lowerHref.contains("/projekte/")
                || lowerHref.contains("projekt-")
                || lowerUrl.contains("/projekt/"));
    }

    private String extractDescription(Document document) {
        String description = firstText(document,
                "[data-testid*=description]",
                ".project-description",
                ".description",
                ".content",
                "main");
        if (description.isBlank()) {
            description = document.body().text();
        }
        return trimTo(description, 12000);
    }

    private String firstText(Document document, String... selectors) {
        for (String selector : selectors) {
            Element element = document.selectFirst(selector);
            if (element != null) {
                String text = element.text().trim();
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
                .compile("(\\d{2,4})(?:[,.]\\d{1,2})?\\s*(?:EUR|€)\\s*(?:/|pro)?\\s*(?:h|std|stunde|tag|td)?",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (matcher.find()) {
            return new BigDecimal(matcher.group(1));
        }
        return null;
    }

    private String cleanTitle(String title) {
        return title.replace(" | freelancermap", "").trim();
    }

    private String normalizeUrl(String url) {
        int queryStart = url.indexOf('?');
        String normalized = queryStart >= 0 ? url.substring(0, queryStart) : url;
        return normalized.trim();
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
