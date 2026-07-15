package com.prognimak.jobscanner.scanner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prognimak.jobscanner.config.JobScannerProperties;
import com.prognimak.jobscanner.entity.ContractType;
import com.prognimak.jobscanner.entity.JobOffer;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import com.prognimak.jobscanner.entity.RemoteType;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
public class MeinestadtScanner implements JobSourceScanner {

    private static final Logger log = LoggerFactory.getLogger(MeinestadtScanner.class);
    private static final ZoneId PORTAL_ZONE = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter GERMAN_DATE = DateTimeFormatter.ofPattern("d.M.uuuu");
    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.ofPattern("uuuuMMdd");
    private static final String JS_LIB_PARAMS_PREFIX = "var jsLibParamsIn = JSON.parse(\"";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;
    private final JobScannerProperties.Meinestadt properties;

    public MeinestadtScanner(RestClient.Builder restClientBuilder, JobScannerProperties properties) {
        this.properties = properties.getScanners().getMeinestadt();
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
        return "meinestadt";
    }

    @Override
    public List<JobOffer> scan(JobSearchCriteria criteria) {
        if (!properties.isEnabled() || !isGermany(criteria)) {
            return List.of();
        }

        long startedAt = System.nanoTime();
        List<JobOffer> candidates = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        for (int page = 1; page <= Math.max(1, properties.getMaxPages()); page++) {
            String searchUrl = buildSearchUrl(criteria, page);
            try {
                String html = fetch(searchUrl);
                List<JobOffer> pageOffers = extractOffersFromSearchPage(html, searchUrl);
                for (JobOffer offer : pageOffers) {
                    if (seenUrls.add(offer.getJobUrl())) {
                        candidates.add(offer);
                    }
                    if (candidates.size() >= Math.max(1, properties.getMaxCandidates())) {
                        break;
                    }
                }
                log.info("Meinestadt scan page {} summary: candidates={}, uri={}",
                        page, pageOffers.size(), searchUrl);
                delay();
            } catch (RuntimeException ex) {
                throw new ScannerBlockedException(source(),
                        "meinestadt request failed or timed out after "
                                + properties.getRequestTimeoutSeconds()
                                + " seconds.",
                        ex);
            }
        }

        List<JobOffer> offers = fetchDetailsInParallel(candidates, criteria);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("Meinestadt scan finished: candidates={}, imported={}, elapsedMs={}",
                candidates.size(), offers.size(), elapsedMs);
        return offers;
    }

    String buildSearchUrl(JobSearchCriteria criteria, int page) {
        String locationPath = "deutschland";
        if (criteria != null && hasText(criteria.getLocation()) && !isBroadLocationFilter(criteria.getLocation())) {
            locationPath = toMeinestadtLocationSlug(criteria.getLocation());
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/")
                .path(locationPath);

        if (criteria != null && hasText(criteria.getKeyword())) {
            builder.path("/suche");
            builder.queryParam("words", criteria.getKeyword().trim());
        }
        if (page > 1) {
            builder.queryParam("page", page);
        }
        return builder.build().encode(StandardCharsets.UTF_8).toUriString();
    }

    List<JobOffer> extractOffersFromSearchPage(String html, String baseUrl) {
        Document document = Jsoup.parse(html, baseUrl);
        List<JobOffer> offers = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        for (JobOffer offer : extractOffersFromEmbeddedJson(html)) {
            if (seenUrls.add(offer.getJobUrl())) {
                offers.add(offer);
            }
        }
        for (Element card : document.select("li.m-resultListEntryJobScan")) {
            Element titleLink = card.selectFirst("a.m-resultListEntryJobScan__clickArea[href], a[href*=/premium?id=]");
            if (titleLink == null) {
                continue;
            }
            String title = clean(card.selectFirst(".m-resultListEntryJobScan__headline") == null
                    ? titleLink.attr("title")
                    : card.selectFirst(".m-resultListEntryJobScan__headline").text());
            String jobUrl = normalizeUrl(titleLink.absUrl("href"));
            if (!hasText(title) || !isJobDetailUrl(jobUrl)) {
                continue;
            }

            JobOffer offer = new JobOffer();
            offer.setSource(source());
            offer.setTitle(title);
            offer.setCompany(clean(firstText(card, ".m-resultListEntryJobScan__company")));
            offer.setLocation(clean(firstText(card, ".m-resultListEntryJobScan__location")));
            offer.setRemoteType(detectRemoteType(card.text()));
            offer.setContractType(detectContractType(card.text()));
            offer.setDescription(trimTo(clean(card.text()), 4000));
            offer.setJobUrl(jobUrl);
            offer.setDetectedAt(Instant.now());
            offer.setPublishedAt(extractPublishedAtFromCard(card, titleLink).orElse(null));
            if (seenUrls.add(offer.getJobUrl())) {
                offers.add(offer);
            }
        }
        return offers;
    }

    private List<JobOffer> extractOffersFromEmbeddedJson(String html) {
        List<JobOffer> offers = new ArrayList<>();
        for (String encodedJson : extractJsonParseArguments(html)) {
            try {
                String json = objectMapper.readValue("\"" + encodedJson + "\"", String.class);
                JsonNode root = objectMapper.readTree(json);
                JsonNode items = root.path("results").path("items");
                if (!items.isArray()) {
                    continue;
                }
                for (JsonNode item : items) {
                    toOffer(item).ifPresent(offers::add);
                }
            } catch (JsonProcessingException ex) {
                log.debug("Could not parse meinestadt embedded result JSON", ex);
            }
        }
        return offers;
    }

    private List<String> extractJsonParseArguments(String html) {
        List<String> values = new ArrayList<>();
        int searchFrom = 0;
        while (searchFrom < html.length()) {
            int start = html.indexOf(JS_LIB_PARAMS_PREFIX, searchFrom);
            if (start < 0) {
                return values;
            }
            int valueStart = start + JS_LIB_PARAMS_PREFIX.length();
            int valueEnd = findJavaScriptStringEnd(html, valueStart);
            if (valueEnd < 0) {
                return values;
            }
            values.add(html.substring(valueStart, valueEnd));
            searchFrom = valueEnd + 1;
        }
        return values;
    }

    private int findJavaScriptStringEnd(String html, int valueStart) {
        for (int i = valueStart; i < html.length(); i++) {
            if (html.charAt(i) == '"' && !isEscaped(html, i)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isEscaped(String html, int quoteIndex) {
        int backslashCount = 0;
        for (int i = quoteIndex - 1; i >= 0 && html.charAt(i) == '\\'; i--) {
            backslashCount++;
        }
        return backslashCount % 2 == 1;
    }

    private Optional<JobOffer> toOffer(JsonNode item) {
        String title = clean(text(item, "title"));
        String jobUrl = normalizeUrl(text(item, "detailUrl"));
        if (!hasText(title) || !isJobDetailUrl(jobUrl)) {
            return Optional.empty();
        }

        String description = buildJsonDescription(item);
        JobOffer offer = new JobOffer();
        offer.setSource(source());
        offer.setTitle(title);
        offer.setCompany(clean(text(item, "companyName")));
        offer.setLocation(clean(firstNonBlank(
                text(item, "workCity"),
                text(item, "otherWorkCitiesStripped"))));
        offer.setRemoteType(detectRemoteType(description));
        offer.setContractType(detectContractType(description));
        offer.setDescription(trimTo(description, 4000));
        offer.setJobUrl(jobUrl);
        offer.setDetectedAt(Instant.now());
        offer.setPublishedAt(parseGermanDate(firstNonBlank(
                text(item, "mslayerElementStartDate"),
                text(item, "labelText"))).orElse(null));
        return Optional.of(offer);
    }

    private String buildJsonDescription(JsonNode item) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, text(item, "descriptionTextFull"));
        addIfPresent(parts, text(item, "descriptionText"));
        addLabeledIfPresent(parts, "Titel", text(item, "title"));
        addLabeledIfPresent(parts, "Firma", text(item, "companyName"));
        addLabeledIfPresent(parts, "Ort", text(item, "workCity"));
        addLabeledIfPresent(parts, "Arbeitszeit", text(item, "workTime"));
        addLabeledIfPresent(parts, "Anstellungsart", text(item, "jobOfferType"));
        addLabeledIfPresent(parts, "Gehalt", text(item, "salaryLabel"));

        List<String> tags = new ArrayList<>();
        JsonNode seoTags = item.path("seoTags");
        if (seoTags.isArray()) {
            seoTags.forEach(tag -> addIfPresent(tags, text(tag, "tag")));
        }
        if (!tags.isEmpty()) {
            parts.add("Tags: " + String.join(", ", tags));
        }

        JsonNode categories = item.path("categoryTrackings");
        if (categories.isArray()) {
            List<String> categoryTexts = new ArrayList<>();
            categories.forEach(category -> addIfPresent(categoryTexts, category.asText("")));
            if (!categoryTexts.isEmpty()) {
                parts.add("Kategorien: " + String.join("; ", categoryTexts));
            }
        }

        return clean(String.join("\n", parts));
    }

    Optional<JobOffer> parseDetail(String html, String detailUrl, JobOffer fallback) {
        Document document = Jsoup.parse(html, detailUrl);
        JobOffer offer = new JobOffer();
        offer.setSource(source());
        offer.setTitle(firstNonBlank(
                clean(firstText(document, ".ms-jobDetailHeader h1")),
                clean(firstText(document, "h1")),
                fallback == null ? "" : fallback.getTitle()));
        offer.setCompany(firstNonBlank(
                clean(firstText(document, ".ms-jobDetailHeader__companyName")),
                extractDataMstValue(html, "mslayer_element_company_name"),
                fallback == null ? "" : fallback.getCompany()));
        offer.setLocation(firstNonBlank(
                extractLocationFromDescription(document),
                fallback == null ? "" : fallback.getLocation()));
        String description = extractDescription(document);
        offer.setDescription(hasText(description) ? description : fallback == null ? "" : fallback.getDescription());
        offer.setRemoteType(firstNonNull(detectRemoteType(document.text()), fallback == null ? null : fallback.getRemoteType()));
        offer.setContractType(firstNonNull(detectContractType(document.text()), fallback == null ? null : fallback.getContractType()));
        offer.setJobUrl(normalizeUrl(detailUrl));
        offer.setDetectedAt(Instant.now());
        offer.setPublishedAt(extractPublishedAtFromDetail(document, html)
                .orElse(fallback == null ? null : fallback.getPublishedAt()));

        if (!hasText(offer.getTitle()) || !hasText(offer.getJobUrl())) {
            return Optional.empty();
        }
        return Optional.of(offer);
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
                    log.warn("Meinestadt detail worker failed", ex);
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
            Optional<JobOffer> parsedOffer = parseDetail(html, cardOffer.getJobUrl(), cardOffer);
            if (parsedOffer.isEmpty()) {
                return Optional.empty();
            }
            JobOffer offer = parsedOffer.get();
            return matchesLocalCriteria(offer, criteria) ? Optional.of(offer) : Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("Meinestadt detail request failed for {}: {}", cardOffer.getJobUrl(), ex.getMessage());
            return matchesLocalCriteria(cardOffer, criteria) ? Optional.of(cardOffer) : Optional.empty();
        } finally {
            if (acquired) {
                concurrencyLimit.release();
            }
        }
    }

    private String fetch(String url) {
        return restClient.get()
                .uri(URI.create(url))
                .retrieve()
                .body(String.class);
    }

    private Optional<Instant> extractPublishedAtFromCard(Element card, Element titleLink) {
        String date = clean(firstText(card, ".m-resultListEntryJobScan__date"));
        Optional<Instant> parsed = parseGermanDate(date);
        if (parsed.isPresent()) {
            return parsed;
        }
        return parseGermanDate(extractAttributeJsonValue(titleLink, "data-mst", "mslayer_element_detail_start_date"));
    }

    private Optional<Instant> extractPublishedAtFromDetail(Document document, String html) {
        Optional<Instant> toolbarDate = parseGermanDate(firstText(document, ".ms-jobDetailToolbar__date"));
        if (toolbarDate.isPresent()) {
            return toolbarDate;
        }
        return parseGermanDate(extractDataMstValue(html, "mslayer_element_detail_start_date"));
    }

    private String extractDescription(Document document) {
        List<String> blocks = new ArrayList<>();
        for (Element article : document.select(".ms-jobDetailStyledText")) {
            String text = clean(article.text());
            if (hasText(text) && !text.toLowerCase(Locale.ROOT).contains("dann freuen wir uns auf deine")) {
                blocks.add(text);
            }
        }
        if (blocks.isEmpty()) {
            blocks.add(clean(firstText(document, "main")));
        }
        return trimTo(String.join("\n\n", blocks), 12000);
    }

    private String extractLocationFromDescription(Document document) {
        String text = document.text();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)(?:Adresse|Arbeitsort|Einsatzort)\\s+([^\\n|]+?)(?:\\s{2,}|Job Art|Vertragsart|$)")
                .matcher(text);
        if (matcher.find()) {
            return clean(matcher.group(1));
        }
        return "";
    }

    private RemoteType detectRemoteType(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (lower.contains("hybrid")) {
            return RemoteType.HYBRID;
        }
        if (lower.contains("remote") || lower.contains("homeoffice") || lower.contains("home office")
                || lower.contains("heimarbeit") || lower.contains("arbeiten von zuhause")) {
            return RemoteType.REMOTE;
        }
        if (lower.contains("vor ort")) {
            return RemoteType.ON_SITE;
        }
        return null;
    }

    private ContractType detectContractType(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (lower.contains("freelance") || lower.contains("freiberuf") || lower.contains("selbständig")
                || lower.contains("selbststaendig")) {
            return ContractType.FREELANCE;
        }
        if (lower.contains("festanstellung") || lower.contains("unbefristet") || lower.contains("vollzeit")
                || lower.contains("teilzeit") || lower.contains("mini-job") || lower.contains("minijob")) {
            return ContractType.PERMANENT;
        }
        return ContractType.PERMANENT;
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
        if (hasText(criteria.getLocation()) && !isBroadLocationFilter(criteria.getLocation())
                && hasText(offer.getLocation())
                && !offer.getLocation().toLowerCase(Locale.ROOT)
                .contains(criteria.getLocation().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (hasText(criteria.getKeyword())) {
            return keywordMatches(criteria.getKeyword(), offer);
        }
        return true;
    }

    private boolean keywordMatches(String keyword, JobOffer offer) {
        String haystack = (offer.getTitle() + " " + offer.getCompany() + " " + offer.getDescription())
                .toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(keyword.split("[,;\\s]+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .map(token -> token.toLowerCase(Locale.ROOT))
                .anyMatch(haystack::contains);
    }

    private Optional<Instant> parseGermanDate(String value) {
        if (!hasText(value)) {
            return Optional.empty();
        }
        String normalized = value.trim().replaceAll("[^0-9.]", "");
        try {
            if (normalized.matches("\\d{8}")) {
                return Optional.of(LocalDate.parse(normalized, COMPACT_DATE).atStartOfDay(PORTAL_ZONE).toInstant());
            }
            if (normalized.matches("\\d{1,2}\\.\\d{1,2}\\.\\d{4}")) {
                return Optional.of(LocalDate.parse(normalized, GERMAN_DATE).atStartOfDay(PORTAL_ZONE).toInstant());
            }
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private String firstText(Document document, String selector) {
        Element element = document.selectFirst(selector);
        return element == null ? "" : element.text();
    }

    private String firstText(Element element, String selector) {
        Element found = element.selectFirst(selector);
        return found == null ? "" : found.text();
    }

    private String extractDataMstValue(String html, String fieldName) {
        Matcher matcher = Pattern
                .compile("\"" + java.util.regex.Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(html);
        return matcher.find() ? Jsoup.parse(matcher.group(1)).text() : "";
    }

    private String extractAttributeJsonValue(Element element, String attribute, String fieldName) {
        String value = element.attr(attribute);
        Matcher matcher = Pattern
                .compile("\"" + java.util.regex.Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(value);
        return matcher.find() ? Jsoup.parse(matcher.group(1)).text() : "";
    }

    private boolean isJobDetailUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith(properties.getBaseUrl().toLowerCase(Locale.ROOT))
                && (lower.contains("/premium?id=") || lower.contains("/job?id="))
                || lower.startsWith("https://www.meinestadt.de/")
                && lower.contains("/redirect/jobs-redirect");
    }

    private boolean isGermany(JobSearchCriteria criteria) {
        if (criteria == null || !hasText(criteria.getCountry())) {
            return true;
        }
        String country = criteria.getCountry().trim().toLowerCase(Locale.ROOT);
        return country.equals("de") || country.equals("deutschland") || country.equals("germany");
    }

    private boolean isBroadLocationFilter(String location) {
        String normalized = location.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("de")
                || normalized.equals("deutschland")
                || normalized.equals("germany")
                || normalized.equals("remote")
                || normalized.equals("homeoffice")
                || normalized.equals("home office")
                || normalized.equals("hybrid");
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

    private String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        int hashIndex = url.indexOf('#');
        return hashIndex >= 0 ? url.substring(0, hashIndex) : url;
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private String trimTo(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void addIfPresent(List<String> values, String value) {
        if (hasText(value)) {
            values.add(clean(value));
        }
    }

    private void addLabeledIfPresent(List<String> values, String label, String value) {
        if (hasText(value)) {
            values.add(label + ": " + clean(value));
        }
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private String toMeinestadtLocationSlug(String location) {
        String normalized = location.trim().toLowerCase(Locale.ROOT)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss");
        String slug = normalized.replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return hasText(slug) ? slug : "deutschland";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
