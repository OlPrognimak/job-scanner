package com.prognimak.jobscanner.scanner;

import com.prognimak.jobscanner.config.JobScannerProperties;
import com.prognimak.jobscanner.entity.ContractType;
import com.prognimak.jobscanner.entity.JobOffer;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import com.prognimak.jobscanner.entity.RemoteType;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class FreelancermapScanner implements JobSourceScanner {

    private static final Logger log = LoggerFactory.getLogger(FreelancermapScanner.class);
    private static final Set<String> GENERIC_TITLES = Set.of(
            "finden sie das passende projekt",
            "freelance projekte finden",
            "projektboerse",
            "projektbörse"
    );

    private final RestClient restClient;
    private final JobScannerProperties.Freelancermap properties;

    public FreelancermapScanner(RestClient.Builder restClientBuilder, JobScannerProperties properties) {
        this.properties = properties.getScanners().getFreelancermap();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(1, this.properties.getRequestTimeoutSeconds()));
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
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

        long startedAt = System.nanoTime();
        List<List<JobOffer>> offersByPage = new ArrayList<>();
        for (int page = 1; page <= Math.max(1, properties.getMaxPages()); page++) {
            String searchUrl = buildSearchUrl(criteria, page);
            try {
                log.info("Freelancermap scan page {} started: keyword={}, location={}",
                        page, criteria.getKeyword(), criteria.getLocation());
                String html = fetch(searchUrl);
                List<JobOffer> pageOffers = extractOffersFromSearchPage(html, searchUrl, null);
                offersByPage.add(pageOffers);
                delay();
            } catch (RuntimeException ex) {
                throw new ScannerBlockedException(source(),
                        "freelancermap request failed or timed out after "
                                + properties.getRequestTimeoutSeconds()
                                + " seconds.",
                        ex);
            }
        }

        List<JobOffer> candidates = selectAcrossPages(offersByPage);
        List<JobOffer> offers = fetchDetailsInParallel(candidates, criteria);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("Freelancermap scan finished: pages={}, candidates={}, imported={}, elapsedMs={}",
                Math.max(1, properties.getMaxPages()), candidates.size(), offers.size(), elapsedMs);
        return offers;
    }

    private List<JobOffer> fetchDetailsInParallel(List<JobOffer> candidates, JobSearchCriteria criteria) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        int maxDetails = Math.max(1, properties.getMaxDetails());
        int maxParallelRequests = Math.max(1, properties.getMaxParallelDetailRequests());
        Semaphore concurrencyLimit = new Semaphore(maxParallelRequests);
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
                    log.warn("Freelancermap detail worker failed", ex);
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
                return Optional.empty();
            }
            JobOffer offer = parsedOffer.get();
            mergeCardFallbacks(offer, cardOffer);
            return Optional.of(offer);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("Freelancermap detail request failed for {}: {}", cardOffer.getJobUrl(), ex.getMessage());
            return matchesLocalCriteria(cardOffer, criteria) ? Optional.of(cardOffer) : Optional.empty();
        } finally {
            if (acquired) {
                concurrencyLimit.release();
            }
        }
    }

    private List<JobOffer> selectAcrossPages(List<List<JobOffer>> offersByPage) {
        int maxCandidates = Math.max(Math.max(1, properties.getMaxDetails()), properties.getMaxCandidates());
        List<JobOffer> selected = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        int maxPageSize = offersByPage.stream().mapToInt(List::size).max().orElse(0);

        for (int index = 0; index < maxPageSize && selected.size() < maxCandidates; index++) {
            for (List<JobOffer> pageOffers : offersByPage) {
                if (index >= pageOffers.size()) {
                    continue;
                }
                JobOffer offer = pageOffers.get(index);
                if (seenUrls.add(offer.getJobUrl())) {
                    selected.add(offer);
                }
                if (selected.size() >= maxCandidates) {
                    break;
                }
            }
        }
        return selected;
    }

    private String buildSearchUrl(JobSearchCriteria criteria, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path(properties.getSearchPath());

        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            builder.queryParam("query", criteria.getKeyword());
        }
        if (criteria.getLocation() != null && !criteria.getLocation().isBlank()
                && !isCountryOnlyLocation(criteria.getLocation())) {
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

    List<JobOffer> extractOffersFromSearchPage(String html, String baseUrl, JobSearchCriteria criteria) {
        Document document = Jsoup.parse(html, baseUrl);
        List<JobOffer> offers = new ArrayList<>();
        for (Element card : document.select(".project-card")) {
            Element titleLink = card.selectFirst("a[data-testid=title][data-id=project-card-title], a[data-id=project-card-title]");
            if (titleLink == null) {
                continue;
            }
            String title = cleanTitle(titleLink.text());
            String jobUrl = normalizeUrl(titleLink.absUrl("href"));
            if (isGenericTitle(title) || !isProjectDetailUrl(titleLink.attr("href"), jobUrl)) {
                continue;
            }

            JobOffer offer = new JobOffer();
            offer.setSource(source());
            offer.setTitle(title);
            offer.setCompany(extractCardCompany(card));
            offer.setLocation(extractCardLocation(card));
            offer.setRemoteType(detectRemoteType(card.text()));
            offer.setContractType(detectContractType(card.text()));
            offer.setDescription(extractCardDescription(card));
            offer.setJobUrl(jobUrl);
            offer.setDetectedAt(Instant.now());
            offer.setRateOrSalary(extractRate(card.text()));
            if (matchesLocalCriteria(offer, criteria)) {
                offers.add(offer);
            }
        }
        return offers;
    }

    Set<String> extractDetailUrls(String html, String baseUrl) {
        return extractOffersFromSearchPage(html, baseUrl, null).stream()
                .map(JobOffer::getJobUrl)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    Optional<JobOffer> parseDetail(String html, String detailUrl) {
        return parseDetail(html, detailUrl, null);
    }

    Optional<JobOffer> parseDetail(String html, String detailUrl, JobSearchCriteria criteria) {
        Document document = Jsoup.parse(html, detailUrl);
        String title = firstText(document,
                "h1.h2.mg-b-display-m",
                ".project-title",
                ".project-detail-title",
                ".project-detail h1",
                ".project-header h2",
                "article h1",
                "main h1",
                "h1",
                "[data-testid*=title]",
                "title");
        title = cleanTitle(title);
        if (isGenericTitle(title)) {
            return Optional.empty();
        }

        JobOffer offer = new JobOffer();
        offer.setSource(source());
        offer.setTitle(title);
        offer.setCompany(extractDetailCompany(document, html));
        offer.setLocation(extractDetailLocation(document, html));
        offer.setRemoteType(detectRemoteType(document.text()));
        offer.setContractType(detectContractType(document.text()));
        offer.setDescription(extractDescription(document));
        offer.setJobUrl(normalizeUrl(detailUrl));
        offer.setDetectedAt(Instant.now());
        offer.setRateOrSalary(extractRate(document.text()));
        if (!matchesLocalCriteria(offer, criteria)) {
            return Optional.empty();
        }
        return Optional.of(offer);
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
        if (offer.getContractType() == null) {
            offer.setContractType(cardOffer.getContractType());
        }
        if (offer.getDescription() == null || offer.getDescription().isBlank()) {
            offer.setDescription(cardOffer.getDescription());
        }
        if (offer.getRateOrSalary() == null) {
            offer.setRateOrSalary(cardOffer.getRateOrSalary());
        }
    }

    private boolean isProjectDetailUrl(String href, String absoluteUrl) {
        if (absoluteUrl.isBlank() || absoluteUrl.contains("#")) {
            return false;
        }
        String lowerHref = href.toLowerCase(Locale.ROOT);
        String lowerUrl = absoluteUrl.toLowerCase(Locale.ROOT);
        if (lowerUrl.contains("/projektboerse")
                || lowerUrl.contains("/freelancer/")
                || lowerUrl.contains("/unternehmen/")
                || lowerUrl.contains("/projektanbieter")
                || lowerUrl.contains("/projekt-akquise")
                || lowerUrl.contains("/ratgeber/")
                || lowerUrl.contains("/blog/")
                || lowerUrl.contains("/login")
                || lowerUrl.contains("/registrieren")) {
            return false;
        }
        return lowerUrl.startsWith(properties.getBaseUrl().toLowerCase(Locale.ROOT))
                && (lowerHref.contains("/projekt/")
                || lowerHref.contains("/projekte/")
                || lowerUrl.contains("/projekt/")
                || lowerUrl.contains("/projekte/"));
    }

    private String extractDescription(Document document) {
        String description = firstText(document,
                "[data-testid*=description]",
                ".project-description",
                ".project-show-description",
                ".project-details",
                ".project-content",
                "section:contains(Projektbeschreibung)",
                ".description",
                ".content",
                "main");
        if (description.isBlank()) {
            description = document.body().text();
        }
        return trimTo(description, 12000);
    }

    private String extractDetailCompany(Document document, String html) {
        String company = extractProjectJsonString(html, "company");
        if (!company.isBlank()) {
            return company;
        }
        return firstText(document,
                "[data-testid*=company]",
                ".company",
                ".client",
                ".project-company",
                ".customer");
    }

    private String extractDetailLocation(Document document, String html) {
        String city = extractProjectJsonString(html, "city");
        String country = extractProjectJsonString(html, "localizedName");
        if (!city.isBlank() && !country.isBlank()) {
            return city + ", " + country;
        }
        if (!city.isBlank()) {
            return city;
        }
        String location = firstText(document,
                "[data-testid*=location]",
                ".location",
                ".project-location");
        if (!location.isBlank()) {
            return location;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("Ort:\\s*([^|<]+)")
                .matcher(document.text());
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private String extractProjectJsonString(String html, String fieldName) {
        int projectStart = html.indexOf("\"project\":{");
        String searchArea = projectStart >= 0 ? html.substring(projectStart) : html;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"" + java.util.regex.Pattern.quote(fieldName) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
                .matcher(searchArea);
        if (!matcher.find()) {
            return "";
        }
        return decodeJsonText(matcher.group(1));
    }

    private String decodeJsonText(String value) {
        String decoded = value
                .replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\n", " ")
                .replace("\\r", " ")
                .replace("\\t", " ");
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\\\u([0-9a-fA-F]{4})")
                .matcher(decoded);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, String.valueOf((char) Integer.parseInt(matcher.group(1), 16)));
        }
        matcher.appendTail(result);
        return Jsoup.parse(result.toString()).text().trim();
    }

    private String extractCardCompany(Element card) {
        Element titleBlock = card.selectFirst("a[data-id=project-card-title]");
        if (titleBlock == null) {
            return "";
        }
        Element previous = titleBlock.parent() == null ? null : titleBlock.parent().previousElementSibling();
        return previous == null ? "" : previous.text().trim();
    }

    private String extractCardLocation(Element card) {
        String city = firstText(card, "[data-testid=city]");
        if (!city.isBlank()) {
            return city.replaceAll("\\s+", " ").replace(" ,", ",").trim();
        }
        String text = card.text();
        if (text.toLowerCase(Locale.ROOT).contains("remote")) {
            return "Remote";
        }
        return "";
    }

    private String extractCardDescription(Element card) {
        List<String> parts = new ArrayList<>();
        String company = extractCardCompany(card);
        if (!company.isBlank()) {
            parts.add("Firma: " + company);
        }
        String location = extractCardLocation(card);
        if (!location.isBlank()) {
            parts.add("Ort: " + location);
        }
        String info = firstText(card, ".project-info-list");
        if (!info.isBlank()) {
            parts.add("Rahmendaten: " + info);
        }
        String keywords = firstText(card, "[data-id=project-card-keyword-link]");
        if (!keywords.isBlank()) {
            parts.add("Skills: " + keywords);
        }
        return String.join("\n", parts);
    }

    private String firstText(Element root, String... selectors) {
        for (String selector : selectors) {
            Element element = root.selectFirst(selector);
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

    private ContractType detectContractType(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("festanstellung")) {
            return ContractType.PERMANENT;
        }
        return ContractType.FREELANCE;
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

    private boolean isGenericTitle(String title) {
        if (title == null || title.isBlank()) {
            return true;
        }
        String lowerTitle = title.toLowerCase(Locale.ROOT).trim();
        return GENERIC_TITLES.stream().anyMatch(lowerTitle::contains);
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
                && !isCountryOnlyLocation(criteria.getLocation())
                && offer.getLocation() != null && !offer.getLocation().isBlank()
                && !offer.getLocation().toLowerCase(Locale.ROOT)
                .contains(criteria.getLocation().toLowerCase(Locale.ROOT))) {
            return false;
        }
        return matchesTechnologyDisambiguation(offer, criteria);
    }

    private boolean matchesTechnologyDisambiguation(JobOffer offer, JobSearchCriteria criteria) {
        if (criteria.getKeyword() == null || criteria.getKeyword().isBlank()) {
            return true;
        }
        String normalizedKeyword = criteria.getKeyword().toLowerCase(Locale.ROOT).trim();
        boolean searchesJava = java.util.regex.Pattern.compile("(^|[^a-z0-9])java([^a-z0-9]|$)")
                .matcher(normalizedKeyword)
                .find();
        if (!searchesJava || normalizedKeyword.contains("javascript")) {
            return true;
        }

        String searchableText = (safeText(offer.getTitle()) + " "
                + safeText(offer.getDescription())).toLowerCase(Locale.ROOT);
        boolean hasStandaloneJava = java.util.regex.Pattern.compile("(^|[^a-z0-9])java([^a-z0-9]|$)")
                .matcher(searchableText)
                .find();
        boolean hasJavaScript = java.util.regex.Pattern.compile("java\\s*script|javascript|node\\.js|typescript|react|vue")
                .matcher(searchableText)
                .find();
        return hasStandaloneJava || !hasJavaScript;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private boolean isCountryOnlyLocation(String location) {
        String normalized = location.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("de")
                || normalized.equals("deutschland")
                || normalized.equals("germany")
                || normalized.equals("germania");
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
