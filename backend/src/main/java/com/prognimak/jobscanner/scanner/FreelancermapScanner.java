package com.prognimak.jobscanner.scanner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prognimak.jobscanner.config.JobScannerProperties;
import com.prognimak.jobscanner.entity.ContractType;
import com.prognimak.jobscanner.entity.JobOffer;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import com.prognimak.jobscanner.entity.RemoteType;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class FreelancermapScanner implements JobSourceScanner {

    private static final Logger log = LoggerFactory.getLogger(FreelancermapScanner.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String AJAX_PAGE_CHANGE_PAYLOAD = "{\"changed\":[\"pagenr\"]}";
    private static final Set<String> GENERIC_TITLES = Set.of(
            "finden sie das passende projekt",
            "freelance projekte finden",
            "projektboerse",
            "projektbörse"
    );
    private static final ZoneId PORTAL_ZONE = ZoneId.of("Europe/Berlin");
    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("d.M.uuuu, H:mm 'Uhr'"),
            DateTimeFormatter.ofPattern("dd.MM.uuuu, HH:mm 'Uhr'")
    );
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d.M.uuuu"),
            DateTimeFormatter.ofPattern("dd.MM.uuuu"),
            DateTimeFormatter.ofPattern("d. M. uuuu"),
            DateTimeFormatter.ofPattern("dd. MM. uuuu"),
            DateTimeFormatter.ofPattern("d. MMMM uuuu", Locale.GERMAN),
            DateTimeFormatter.ofPattern("dd. MMMM uuuu", Locale.GERMAN)
    );

    private final RestClient restClient;
    private final JobScannerProperties.Freelancermap properties;
    private final String cookieHeader;

    public FreelancermapScanner(RestClient.Builder restClientBuilder, JobScannerProperties properties) {
        this.properties = properties.getScanners().getFreelancermap();
        this.cookieHeader = resolveCookieHeader(this.properties);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(1, this.properties.getRequestTimeoutSeconds()));
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        RestClient.Builder builder = restClientBuilder
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", this.properties.getUserAgent())
                .defaultHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .defaultHeader("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
                .defaultHeader("Cache-Control", "no-cache")
                .defaultHeader("Pragma", "no-cache");
        if (hasText(this.cookieHeader)) {
            builder.defaultHeader(HttpHeaders.COOKIE, this.cookieHeader);
        }
        this.restClient = builder.build();
        log.info("Freelancermap scanner initialized: authenticatedCookieConfigured={}, cookieLength={}",
                hasText(this.cookieHeader), this.cookieHeader.length());
    }

    @Override
    public String source() {
        return "freelancermap";
    }

    private String resolveCookieHeader(JobScannerProperties.Freelancermap properties) {
        String rawCookieHeader;
        if (hasText(properties.getCookieHeader())) {
            rawCookieHeader = properties.getCookieHeader().trim();
        } else if (hasText(properties.getCookieFile())) {
            try {
                rawCookieHeader = Files.readString(Path.of(properties.getCookieFile()), StandardCharsets.UTF_8).trim();
            } catch (IOException ex) {
                throw new IllegalStateException("Could not read freelancermap cookie file: " + properties.getCookieFile(), ex);
            }
        } else {
            return "";
        }
        String filteredCookieHeader = filterCookieHeader(rawCookieHeader, properties.getCookieNames());
        return hasText(filteredCookieHeader) ? filteredCookieHeader : rawCookieHeader;
    }

    private String filterCookieHeader(String rawCookieHeader, String cookieNames) {
        if (!hasText(rawCookieHeader) || !hasText(cookieNames)) {
            return rawCookieHeader;
        }
        Set<String> allowedNames = java.util.Arrays.stream(cookieNames.split(","))
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (allowedNames.isEmpty()) {
            return rawCookieHeader;
        }
        List<String> selectedCookies = new ArrayList<>();
        for (String cookie : rawCookieHeader.split(";\\s*")) {
            int separator = cookie.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String name = cookie.substring(0, separator).trim();
            if (allowedNames.contains(name)) {
                selectedCookies.add(cookie.trim());
            }
        }
        return String.join("; ", selectedCookies);
    }

    @Override
    public List<JobOffer> scan(JobSearchCriteria criteria) {
        if (!properties.isEnabled()) {
            return List.of();
        }

        long startedAt = System.nanoTime();
        List<List<JobOffer>> offersByPage = fetchSearchResultPagesWithHttp(criteria);

        List<JobOffer> candidates = selectAcrossPages(offersByPage);
        List<JobOffer> offers = fetchDetailsInParallel(candidates, criteria);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("Freelancermap scan finished: pages={}, candidates={}, imported={}, elapsedMs={}",
                Math.max(1, properties.getMaxPages()), candidates.size(), offers.size(), elapsedMs);
        return offers;
    }

    private List<List<JobOffer>> fetchSearchResultPagesWithHttp(JobSearchCriteria criteria) {
        List<List<JobOffer>> offersByPage = new ArrayList<>();
        for (int page = 1; page <= Math.max(1, properties.getMaxPages()); page++) {
            String searchUrl = buildSearchUrl(criteria, page);
            try {
                log.info("Freelancermap scan page {} started: url={}", page, searchUrl);
                List<JobOffer> pageOffers = page == 1
                        ? fetchSearchResultPageWithHtml(searchUrl)
                        : fetchSearchResultPageWithAjax(criteria, page, buildAjaxRefererUrl(criteria));
                log.info("Freelancermap scan page {} extracted {} candidate cards", page, pageOffers.size());
                log.info("Freelancermap HTTP scan page {} ids: {}", page, describeOffers(pageOffers));
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
        return offersByPage;
    }

    private List<JobOffer> fetchSearchResultPageWithHtml(String searchUrl) {
        String html = fetch(searchUrl);
        return extractOffersFromSearchPage(html, searchUrl, null);
    }

    private List<JobOffer> fetchSearchResultPageWithAjax(JobSearchCriteria criteria, int requestedPage, String referer) {
        String ajaxUrl = buildAjaxSearchUrl(criteria, requestedPage);
        String json = restClient.post()
                .uri(URI.create(ajaxUrl))
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, "*/*")
                .header(HttpHeaders.ORIGIN, properties.getBaseUrl())
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", referer)
                .header("DNT", "1")
                .header("Priority", "u=1, i")
                .header("Sec-CH-UA", "\"Not;A=Brand\";v=\"8\", \"Chromium\";v=\"150\", \"Google Chrome\";v=\"150\"")
                .header("Sec-CH-UA-Mobile", "?0")
                .header("Sec-CH-UA-Platform", "\"macOS\"")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .body(AJAX_PAGE_CHANGE_PAYLOAD)
                .retrieve()
                .body(String.class);
        List<JobOffer> offers = extractOffersFromAjaxResult(json, ajaxUrl, criteria);
        int returnedPage = extractAjaxCurrentPage(json);
        log.info("Freelancermap AJAX page diagnostic: requestedPage={}, returnedPage={}, cookieConfigured={}, cookieLength={}, ids={}",
                requestedPage,
                returnedPage,
                hasText(cookieHeader),
                cookieHeader.length(),
                describeOffers(offers));
        if (returnedPage != requestedPage) {
            log.warn("Freelancermap AJAX returned page {} for requested page {}. url={}",
                    returnedPage, requestedPage, ajaxUrl);
        }
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
                extractRawPublishedAt(html).ifPresent(cardOffer::setPublishedAt);
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
        int rawCandidates = 0;
        int duplicates = 0;

        for (List<JobOffer> pageOffers : offersByPage) {
            for (JobOffer offer : pageOffers) {
                rawCandidates++;
                String key = dedupeKey(offer);
                if (seenUrls.add(key)) {
                    selected.add(offer);
                } else {
                    duplicates++;
                }
                if (selected.size() >= maxCandidates) {
                    break;
                }
            }
            if (selected.size() >= maxCandidates) {
                break;
            }
        }
        log.info("Freelancermap candidate merge finished: rawCards={}, uniqueCandidates={}, duplicates={}, maxCandidates={}",
                rawCandidates, selected.size(), duplicates, maxCandidates);
        log.info("Freelancermap selected candidate ids: {}", describeOffers(selected));
        return selected;
    }

    private String dedupeKey(JobOffer offer) {
        if (offer.getSourceJobId() != null && !offer.getSourceJobId().isBlank()) {
            return offer.getSource() + ":" + offer.getSourceJobId();
        }
        return offer.getJobUrl();
    }

    private String describeOffers(List<JobOffer> offers) {
        int limit = 80;
        List<String> descriptions = offers.stream()
                .limit(limit)
                .map(offer -> "%s:%s".formatted(
                        offer.getSourceJobId() == null || offer.getSourceJobId().isBlank()
                                ? extractProjectSlug(offer.getJobUrl())
                                : offer.getSourceJobId(),
                        trimTo(safeText(offer.getTitle()).replaceAll("\\s+", " "), 80)))
                .toList();
        String suffix = offers.size() > limit ? " ... +" + (offers.size() - limit) + " more" : "";
        return descriptions + suffix;
    }

    private String buildSearchUrl(JobSearchCriteria criteria, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path(properties.getSearchPath());

        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            builder.queryParam("query", criteria.getKeyword());
        }
        builder.queryParam("countries[]", 1)
                .queryParam("sort", 1);
        if (criteria.getLocation() != null && !criteria.getLocation().isBlank()
                && !isCountryOnlyLocation(criteria.getLocation())) {
            builder.queryParam("city", criteria.getLocation());
        }
        if (page > 1) {
            builder.queryParam("pagenr", page);
        }
        return builder.build().encode(StandardCharsets.UTF_8).toUriString();
    }

    private String buildAjaxSearchUrl(JobSearchCriteria criteria, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/project/search/ajax");

        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            builder.queryParam("query", criteria.getKeyword());
        }
        builder.queryParam("countries[]", 1)
                .queryParam("sort", 1)
                .queryParam("pagenr", page);
        if (criteria.getLocation() != null && !criteria.getLocation().isBlank()
                && !isCountryOnlyLocation(criteria.getLocation())) {
            builder.queryParam("city", criteria.getLocation());
        }
        return builder.build().encode(StandardCharsets.UTF_8).toUriString();
    }

    private String buildAjaxRefererUrl(JobSearchCriteria criteria) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path(properties.getSearchPath());
        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            builder.queryParam("query", criteria.getKeyword());
        }
        return builder.build().encode(StandardCharsets.UTF_8).toUriString();
    }

    private String fetch(String url) {
        return restClient.get()
                .uri(URI.create(url))
                .retrieve()
                .body(String.class);
    }

    private Map<String, String> extractProjectIdsBySlug(String html) {
        Map<String, String> idsBySlug = new HashMap<>();
        if (html == null || html.isBlank()) {
            return idsBySlug;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"id\"\\s*:\\s*(\\d+)\\s*,\\s*\"slug\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
                .matcher(html);
        while (matcher.find()) {
            idsBySlug.put(decodeJsonText(matcher.group(2)), matcher.group(1));
        }
        return idsBySlug;
    }

    private String extractProjectId(String href, String jobUrl, Map<String, String> projectIdsBySlug) {
        String idFromHref = extractQueryParameter(href, "id");
        if (!idFromHref.isBlank()) {
            return idFromHref;
        }
        String idFromUrl = extractQueryParameter(jobUrl, "id");
        if (!idFromUrl.isBlank()) {
            return idFromUrl;
        }
        String slug = extractProjectSlug(jobUrl);
        return projectIdsBySlug.getOrDefault(slug, "");
    }

    private String extractQueryParameter(String url, String parameterName) {
        if (url == null || url.isBlank()) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?:[?&]|&amp;)" + java.util.regex.Pattern.quote(parameterName) + "=(\\d+)")
                .matcher(url);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractProjectSlug(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String normalized = normalizeUrl(url);
        int marker = normalized.indexOf("/projekt/");
        if (marker < 0) {
            return "";
        }
        String slug = normalized.substring(marker + "/projekt/".length());
        int slash = slug.indexOf('/');
        return slash >= 0 ? slug.substring(0, slash) : slug;
    }

    List<JobOffer> extractOffersFromSearchPage(String html, String baseUrl, JobSearchCriteria criteria) {
        Document document = Jsoup.parse(html, baseUrl);
        Map<String, String> projectIdsBySlug = extractProjectIdsBySlug(html);
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
            offer.setSourceJobId(extractProjectId(titleLink.attr("href"), jobUrl, projectIdsBySlug));
            offer.setTitle(title);
            offer.setCompany(extractCardCompany(card));
            offer.setLocation(extractCardLocation(card));
            offer.setRemoteType(detectRemoteType(card.text()));
            offer.setContractType(detectContractType(card.text()));
            offer.setDescription(extractCardDescription(card));
            offer.setJobUrl(jobUrl);
            offer.setDetectedAt(Instant.now());
            offer.setPublishedAt(extractCardPublishedAt(card).orElse(null));
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
        offer.setSourceJobId(extractProjectId(detailUrl, normalizeUrl(detailUrl), Map.of()));
        offer.setTitle(title);
        offer.setCompany(extractDetailCompany(document, html));
        offer.setLocation(extractDetailLocation(document, html));
        offer.setRemoteType(detectRemoteType(document.text()));
        offer.setContractType(detectContractType(document.text()));
        offer.setDescription(extractDescription(document));
        offer.setJobUrl(normalizeUrl(detailUrl));
        offer.setDetectedAt(Instant.now());
        offer.setPublishedAt(extractOriginalPublishedAt(document, html).orElse(null));
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
        if (cardOffer.getPublishedAt() != null) {
            offer.setPublishedAt(cardOffer.getPublishedAt());
        }
        if (offer.getSourceJobId() == null || offer.getSourceJobId().isBlank()) {
            offer.setSourceJobId(cardOffer.getSourceJobId());
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

    List<JobOffer> extractOffersFromAjaxResult(String json, String baseUrl, JobSearchCriteria criteria) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode projects = root.path("projects");
            if (!projects.isArray()) {
                return List.of();
            }
            List<JobOffer> offers = new ArrayList<>();
            for (JsonNode project : projects) {
                Optional<JobOffer> offer = mapAjaxProject(project, baseUrl);
                if (offer.isPresent() && matchesLocalCriteria(offer.get(), criteria)) {
                    offers.add(offer.get());
                }
            }
            return offers;
        } catch (JsonProcessingException ex) {
            throw new ScannerBlockedException(source(), "freelancermap AJAX response was not valid JSON.", ex);
        }
    }

    private int extractAjaxCurrentPage(String json) {
        if (json == null || json.isBlank()) {
            return -1;
        }
        try {
            return OBJECT_MAPPER.readTree(json).path("currentPage").asInt(-1);
        } catch (JsonProcessingException ex) {
            return -1;
        }
    }

    private Optional<JobOffer> mapAjaxProject(JsonNode project, String baseUrl) {
        String id = text(project, "id");
        String title = cleanTitle(text(project, "title"));
        String jobUrl = normalizeAjaxProjectUrl(project, baseUrl);
        if (id.isBlank() || isGenericTitle(title) || jobUrl.isBlank()) {
            return Optional.empty();
        }

        JobOffer offer = new JobOffer();
        offer.setSource(source());
        offer.setSourceJobId(id);
        offer.setTitle(title);
        offer.setCompany(text(project, "company"));
        offer.setLocation(extractAjaxLocation(project));
        offer.setRemoteType(detectAjaxRemoteType(project));
        offer.setContractType(detectAjaxContractType(project));
        offer.setDescription(extractAjaxDescription(project));
        offer.setJobUrl(jobUrl);
        offer.setDetectedAt(Instant.now());
        offer.setPublishedAt(parsePortalDate(text(project, "created")).orElse(null));
        offer.setRateOrSalary(extractRate(extractAjaxSearchableText(project)));
        return Optional.of(offer);
    }

    private String normalizeAjaxProjectUrl(JsonNode project, String baseUrl) {
        String url = text(project, "url");
        if (url.isBlank()) {
            url = text(project.path("links"), "project");
        }
        if (url.isBlank()) {
            String slug = text(project, "slug");
            if (!slug.isBlank()) {
                url = "/projekt/" + slug;
            }
        }
        if (url.isBlank()) {
            return "";
        }
        return normalizeUrl(Jsoup.parse("<a href=\"" + url + "\"></a>", baseUrl).selectFirst("a").absUrl("href"));
    }

    private String extractAjaxLocation(JsonNode project) {
        String city = text(project, "city");
        String country = text(project.path("country"), "nameDe");
        if (country.isBlank()) {
            country = text(project.path("country"), "name");
        }
        if (!city.isBlank() && !country.isBlank()) {
            return city + ", " + country;
        }
        if (!city.isBlank()) {
            return city;
        }
        JsonNode locations = project.path("locations");
        if (locations.isArray() && !locations.isEmpty()) {
            return text(locations.get(0), "name");
        }
        return "";
    }

    private RemoteType detectAjaxRemoteType(JsonNode project) {
        JsonNode remoteInPercent = project.path("projectContractType").path("remoteInPercent");
        if (remoteInPercent.canConvertToInt()) {
            return remoteInPercent.asInt() > 0 ? RemoteType.REMOTE : RemoteType.ON_SITE;
        }
        return detectRemoteType(extractAjaxSearchableText(project));
    }

    private ContractType detectAjaxContractType(JsonNode project) {
        String type = text(project.path("projectContractType"), "type");
        if (type.equals("permanent_position")) {
            return ContractType.PERMANENT;
        }
        return detectContractType(extractAjaxSearchableText(project));
    }

    private String extractAjaxDescription(JsonNode project) {
        List<String> parts = new ArrayList<>();
        String description = Jsoup.parse(text(project, "description")).text().trim();
        if (!description.isBlank()) {
            parts.add(description);
        }
        String skills = extractAjaxSkills(project);
        if (!skills.isBlank()) {
            parts.add("Skills: " + skills);
        }
        return trimTo(String.join("\n", parts), 12000);
    }

    private String extractAjaxSkills(JsonNode project) {
        JsonNode skills = project.path("skills");
        if (!skills.isArray()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (JsonNode skill : skills) {
            String name = text(skill, "de");
            if (name.isBlank()) {
                name = text(skill, "en");
            }
            if (name.isBlank()) {
                name = text(skill, "name");
            }
            if (!name.isBlank()) {
                values.add(name);
            }
        }
        return String.join(", ", values);
    }

    private String extractAjaxSearchableText(JsonNode project) {
        return String.join(" ",
                text(project, "title"),
                text(project, "description"),
                text(project, "contractType"),
                text(project.path("projectContractType"), "type"),
                text(project.path("projectContractType"), "remoteInPercent"),
                extractAjaxSkills(project));
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
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

    private Optional<Instant> extractOriginalPublishedAt(Element root, String html) {
        Optional<Instant> rawDate = extractRawPublishedAt(html);
        if (rawDate.isPresent()) {
            return rawDate;
        }
        Optional<Instant> headerDate = extractTextPublishedAt(firstText(root, ".project-show-header"));
        if (headerDate.isPresent()) {
            return headerDate;
        }
        Optional<Instant> structuredDate = extractStructuredPublishedAt(root);
        if (structuredDate.isPresent()) {
            return structuredDate;
        }
        Optional<Instant> jsonDate = extractJsonPublishedAt(html);
        if (jsonDate.isPresent()) {
            return jsonDate;
        }
        return extractTextPublishedAt(root.text());
    }

    private Optional<Instant> extractCardPublishedAt(Element card) {
        String created = firstText(card,
                ".project-created span.created",
                ".project-created [data-testid=created]",
                "[data-testid=created]",
                "span.created");
        if (created.isBlank()) {
            return Optional.empty();
        }
        return parseCardCreated(created);
    }

    private Optional<Instant> parseCardCreated(String value) {
        String normalized = value.replace('\u00a0', ' ').trim();
        if (normalized.matches("\\d{1,2}\\.\\d{1,2}\\.\\d{4}")) {
            for (DateTimeFormatter formatter : DATE_FORMATTERS) {
                try {
                    return Optional.of(LocalDate.parse(normalized, formatter)
                            .atStartOfDay(PORTAL_ZONE)
                            .toInstant());
                } catch (DateTimeParseException ignored) {
                    // Try next supported date-only format.
                }
            }
        }
        if (normalized.matches("\\d{1,2}:\\d{2}")) {
            try {
                return Optional.of(LocalDate.now(PORTAL_ZONE)
                        .atTime(LocalTime.parse(normalized, DateTimeFormatter.ofPattern("H:mm")))
                        .atZone(PORTAL_ZONE)
                        .toInstant());
            } catch (DateTimeParseException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private Optional<Instant> extractRawPublishedAt(String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }

        java.util.regex.Matcher headerMatcher = java.util.regex.Pattern
                .compile("(?is)<div[^>]*class=\"[^\"]*project-show-header[^\"]*\"[^>]*>(.*?)</div>")
                .matcher(html);
        while (headerMatcher.find()) {
            Optional<Instant> parsed = extractTextPublishedAt(Jsoup.parse(headerMatcher.group(1)).text());
            if (parsed.isPresent()) {
                return parsed;
            }
        }

        String text = Jsoup.parse(html).text();
        return extractTextPublishedAt(text);
    }

    private Optional<Instant> extractStructuredPublishedAt(Element root) {
        for (String selector : List.of(
                "time[datetime]",
                "[datetime]",
                "meta[itemprop=datePosted]",
                "meta[itemprop=datePublished]",
                "meta[property=article:published_time]",
                "meta[name=date]",
                "meta[name=pubdate]")) {
            Element element = root.selectFirst(selector);
            if (element == null) {
                continue;
            }
            String value = element.hasAttr("datetime") ? element.attr("datetime")
                    : element.hasAttr("content") ? element.attr("content")
                    : element.text();
            Optional<Instant> parsed = parsePortalDate(value);
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    private Optional<Instant> extractJsonPublishedAt(String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"(?:datePosted|datePublished|publishedAt|publicationDate|createdAt|created_at)\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(html);
        while (matcher.find()) {
            Optional<Instant> parsed = parsePortalDate(decodeJsonText(matcher.group(1)));
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    private Optional<Instant> extractTextPublishedAt(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String normalized = text.replace('\u00a0', ' ').replaceAll("\\s+", " ");
        java.util.regex.Matcher dateTimeMatcher = java.util.regex.Pattern
                .compile("(?i)(?:veröffentlicht|veroeffentlicht|eingestellt|online seit|erstellt|aktualisiert)\\s*(?:am)?\\s*:?\\s*(\\d{1,2}\\.\\s*\\d{1,2}\\.\\s*\\d{4}\\s*,\\s*\\d{1,2}:\\d{2}\\s*uhr)")
                .matcher(normalized);
        if (dateTimeMatcher.find()) {
            Optional<Instant> parsed = parsePortalDate(dateTimeMatcher.group(1));
            if (parsed.isPresent()) {
                return parsed;
            }
        }

        java.util.regex.Matcher relativeMatcher = java.util.regex.Pattern
                .compile("(?i)(?:veröffentlicht|veroeffentlicht|eingestellt|online seit|erstellt|aktualisiert)\\s*(?:am|vor)?\\s*(heute|gestern|\\d+\\s+tag(?:e|en)?)")
                .matcher(normalized);
        if (relativeMatcher.find()) {
            Optional<Instant> parsed = parseRelativePortalDate(relativeMatcher.group(1));
            if (parsed.isPresent()) {
                return parsed;
            }
        }

        java.util.regex.Matcher dateMatcher = java.util.regex.Pattern
                .compile("(?i)(?:veröffentlicht|veroeffentlicht|eingestellt|online seit|erstellt|aktualisiert)\\s*(?:am)?\\s*:?\\s*(\\d{1,2}\\.\\s*(?:\\d{1,2}\\.|[A-Za-zÄÖÜäöüß]+)\\s*\\d{4})")
                .matcher(normalized);
        if (dateMatcher.find()) {
            return parsePortalDate(dateMatcher.group(1));
        }
        return Optional.empty();
    }

    private Optional<Instant> parseRelativePortalDate(String value) {
        String normalized = value.toLowerCase(Locale.GERMAN).trim();
        LocalDate today = LocalDate.now(PORTAL_ZONE);
        if (normalized.equals("heute")) {
            return Optional.of(today.atStartOfDay(PORTAL_ZONE).toInstant());
        }
        if (normalized.equals("gestern")) {
            return Optional.of(today.minusDays(1).atStartOfDay(PORTAL_ZONE).toInstant());
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(normalized);
        if (matcher.find()) {
            return Optional.of(today.minusDays(Long.parseLong(matcher.group(1))).atStartOfDay(PORTAL_ZONE).toInstant());
        }
        return Optional.empty();
    }

    private Optional<Instant> parsePortalDate(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        try {
            return Optional.of(Instant.parse(normalized));
        } catch (DateTimeParseException ignored) {
            // Try common German date-only formats below.
        }
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return Optional.of(LocalDateTime.parse(normalized, formatter).atZone(PORTAL_ZONE).toInstant());
            } catch (DateTimeParseException ignored) {
                // Continue with the next known portal date-time format.
            }
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return Optional.of(LocalDate.parse(normalized, formatter).atStartOfDay(PORTAL_ZONE).toInstant());
            } catch (DateTimeParseException ignored) {
                // Continue with the next known portal date format.
            }
        }
        return Optional.empty();
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
