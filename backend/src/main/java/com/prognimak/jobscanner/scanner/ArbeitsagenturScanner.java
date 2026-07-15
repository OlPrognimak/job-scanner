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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class ArbeitsagenturScanner implements JobSourceScanner {

    private static final Logger log = LoggerFactory.getLogger(ArbeitsagenturScanner.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ZoneId GERMANY = ZoneId.of("Europe/Berlin");

    private final RestClient restClient;
    private final JobScannerProperties.Arbeitsagentur properties;

    public ArbeitsagenturScanner(RestClient.Builder restClientBuilder, JobScannerProperties properties) {
        this.properties = properties.getScanners().getArbeitsagentur();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(1, this.properties.getRequestTimeoutSeconds()));
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("X-API-Key", this.properties.getApiKey())
                .build();
    }

    @Override
    public String source() {
        return "arbeitsagentur";
    }

    @Override
    public List<JobOffer> scan(JobSearchCriteria criteria) {
        if (!properties.isEnabled() || !isGermany(criteria)) {
            return List.of();
        }

        long startedAt = System.nanoTime();
        List<JobSummary> summaries = new ArrayList<>();
        for (Integer offerType : offerTypes(criteria)) {
            for (int page = 1; page <= Math.max(1, properties.getMaxPages()); page++) {
                URI uri = buildSearchUri(criteria, page, offerType);
                JsonNode response = fetchJson(uri, "Arbeitsagentur search request failed.");
                JsonNode results = resultArray(response);
                if (!results.isArray() || results.isEmpty()) {
                    log.info("Arbeitsagentur scan page {} returned no results: offerType={}, uri={}",
                            page, offerType, uri);
                    break;
                }
                int pageAdded = 0;
                for (JsonNode result : results) {
                    Optional<JobSummary> summary = mapSummary(result, offerType);
                    if (summary.isPresent()) {
                        summaries.add(summary.get());
                        pageAdded++;
                    }
                    if (summaries.size() >= Math.max(1, properties.getMaxDetails())) {
                        break;
                    }
                }
                log.info("Arbeitsagentur scan page {} summary: offerType={}, apiResults={}, accepted={}, uri={}",
                        page, offerType, results.size(), pageAdded, uri);
                if (summaries.size() >= Math.max(1, properties.getMaxDetails())) {
                    break;
                }
            }
        }

        List<JobOffer> offers = fetchDetails(summaries).stream()
                .filter(offer -> matchesLocalCriteria(offer, criteria))
                .toList();
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("Arbeitsagentur scan finished: candidates={}, imported={}, elapsedMs={}",
                summaries.size(), offers.size(), elapsedMs);
        return offers;
    }

    URI buildSearchUri(JobSearchCriteria criteria, int page, int offerType) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/jobboerse/jobsuche-service/pc/v6/jobs")
                .queryParam("page", Math.max(1, page))
                .queryParam("size", Math.max(1, properties.getResultsPerPage()))
                .queryParam("angebotsart", offerType)
                .queryParam("zeitarbeit", false)
                .queryParam("pav", false);

        if (properties.getPublishedWithinDays() >= 0) {
            builder.queryParam("veroeffentlichtseit", Math.min(100, properties.getPublishedWithinDays()));
        }
        if (criteria != null && hasText(criteria.getKeyword())) {
            builder.queryParam("was", normalizeKeyword(criteria.getKeyword()));
        }
        if (criteria != null && hasText(criteria.getLocation()) && !isBroadLocationFilter(criteria.getLocation())) {
            builder.queryParam("wo", criteria.getLocation().trim());
        }
        if (criteria != null && criteria.getRemoteType() == RemoteType.REMOTE) {
            builder.queryParam("arbeitszeit", "ho");
        }

        return URI.create(builder.build().encode().toUriString());
    }

    JobOffer mapOffer(JobSummary summary, JsonNode details) {
        JobOffer offer = new JobOffer();
        offer.setSource(source());
        offer.setTitle(firstText(
                text(details.path("stellenangebotsTitel")),
                text(details.path("titel")),
                text(details.path("beruf")),
                text(details.path("hauptberuf")),
                summary.title()));
        offer.setCompany(firstText(
                text(details.path("firma")),
                text(details.path("arbeitgeber")),
                text(details.path("arbeitgeberdarstellung").path("name")),
                summary.company()));
        offer.setLocation(firstText(
                textFromArbeitsort(details.path("stellenlokationen")),
                textFromArbeitsort(details.path("arbeitsort")),
                textFromArbeitsort(details.path("arbeitsorte")),
                summary.location()));
        offer.setRemoteType(detectRemoteType(details, summary));
        offer.setContractType(summary.contractType());
        offer.setDescription(cleanDescription(firstText(
                text(details.path("stellenangebotsBeschreibung")),
                text(details.path("beschreibung")),
                text(details.path("aufgaben")),
                summary.description())));
        offer.setJobUrl(firstText(
                text(details.path("externeUrl")),
                text(details.path("bewerbung").path("url")),
                summary.externalUrl(),
                jobDetailUrl(summary.referenceNumber())));
        offer.setDetectedAt(Instant.now());
        offer.setPublishedAt(firstInstant(
                parseDate(details.path("datumErsteVeroeffentlichung").asText("")),
                parseDate(details.path("veroeffentlichungszeitraum").path("von").asText("")),
                parseDate(details.path("aktuelleVeroeffentlichungsdatum").asText("")),
                parseDate(details.path("veroeffentlichungsdatum").asText("")),
                Optional.ofNullable(summary.publishedAt())));
        return offer;
    }

    Optional<JobSummary> mapSummary(JsonNode result, int offerType) {
        String referenceNumber = firstText(text(result.path("referenznummer")), text(result.path("refnr")));
        if (!hasText(referenceNumber)) {
            return Optional.empty();
        }
        String title = firstText(text(result.path("stellenangebotsTitel")), text(result.path("beruf")),
                text(result.path("titel")), text(result.path("hauptberuf")));
        String company = firstText(text(result.path("firma")), text(result.path("arbeitgeber")));
        String location = firstText(textFromArbeitsort(result.path("stellenlokationen")),
                textFromArbeitsort(result.path("arbeitsort")));
        Instant publishedAt = parseDate(result.path("datumErsteVeroeffentlichung").asText(""))
                .or(() -> parseDate(result.path("veroeffentlichungszeitraum").path("von").asText("")))
                .or(() -> parseDate(result.path("aktuelleVeroeffentlichungsdatum").asText("")))
                .orElse(null);
        String externalUrl = result.path("externeUrl").asText("");
        RemoteType remoteType = result.path("homeofficemoeglich").asBoolean(false)
                || result.path("homeofficeprozent").asInt(0) > 0
                ? RemoteType.REMOTE
                : null;
        return Optional.of(new JobSummary(
                referenceNumber,
                title,
                company,
                location,
                "",
                externalUrl,
                publishedAt,
                remoteType,
                offerType == 2 ? ContractType.FREELANCE : ContractType.PERMANENT
        ));
    }

    private JsonNode resultArray(JsonNode response) {
        JsonNode current = response.path("ergebnisliste");
        if (current.isArray()) {
            return current;
        }
        return response.path("stellenangebote");
    }

    private List<JobOffer> fetchDetails(List<JobSummary> summaries) {
        if (summaries.isEmpty()) {
            return List.of();
        }
        int parallelism = Math.max(1, properties.getMaxParallelDetailRequests());
        Semaphore semaphore = new Semaphore(parallelism);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<JobOffer>> futures = summaries.stream()
                    .limit(Math.max(1, properties.getMaxDetails()))
                    .map(summary -> executor.submit(() -> fetchDetail(summary, semaphore)))
                    .toList();
            List<JobOffer> offers = new ArrayList<>();
            for (Future<JobOffer> future : futures) {
                try {
                    JobOffer offer = future.get();
                    if (hasText(offer.getTitle()) && hasText(offer.getJobUrl())) {
                        offers.add(offer);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException ex) {
                    log.warn("Arbeitsagentur detail request failed: {}", ex.getCause().getMessage());
                }
            }
            return offers;
        }
    }

    private JobOffer fetchDetail(JobSummary summary, Semaphore semaphore) throws InterruptedException {
        semaphore.acquire();
        try {
            URI uri = buildDetailUri(summary.referenceNumber());
            JsonNode details = fetchJson(uri, "Arbeitsagentur detail request failed.");
            return mapOffer(summary, details);
        } catch (RuntimeException ex) {
            log.warn("Arbeitsagentur detail request failed for refnr={}: {}", summary.referenceNumber(), ex.getMessage());
            return mapOffer(summary, OBJECT_MAPPER.createObjectNode());
        } finally {
            semaphore.release();
        }
    }

    URI buildDetailUri(String referenceNumber) {
        String encodedReference = Base64.getEncoder()
                .encodeToString(referenceNumber.getBytes(StandardCharsets.UTF_8));
        return UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/jobboerse/jobsuche-service/pc/v4/jobdetails/")
                .pathSegment(encodedReference)
                .build()
                .encode()
                .toUri();
    }

    private JsonNode fetchJson(URI uri, String errorMessage) {
        try {
            String body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                return OBJECT_MAPPER.createObjectNode();
            }
            return OBJECT_MAPPER.readTree(body);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
            throw new ScannerBlockedException(source(),
                    "Arbeitsagentur API request was rejected. The public X-API-Key may have changed.",
                    ex);
        } catch (JsonProcessingException ex) {
            throw new ScannerBlockedException(source(),
                    "Arbeitsagentur API returned invalid JSON for request " + uri + ".",
                    ex);
        } catch (RestClientException ex) {
            throw new ScannerBlockedException(source(), errorMessage + " URI: " + uri, ex);
        }
    }

    private List<Integer> offerTypes(JobSearchCriteria criteria) {
        if (criteria != null && criteria.getContractType() == ContractType.FREELANCE) {
            return List.of(2);
        }
        if (criteria != null && criteria.getContractType() == ContractType.PERMANENT) {
            return List.of(1);
        }
        return List.of(1, 2);
    }

    private String normalizeKeyword(String keyword) {
        return keyword.trim().replace(',', ' ').replace(';', ' ').replaceAll("\\s+", " ");
    }

    private RemoteType detectRemoteType(JsonNode details, JobSummary summary) {
        if (details.path("homeofficemoeglich").asBoolean(false)
                || details.path("homeofficeprozent").asInt(0) > 0) {
            return RemoteType.REMOTE;
        }
        String text = (details.toString() + " " + summary.description() + " " + summary.location())
                .toLowerCase(Locale.ROOT);
        if (text.contains("hybrid")) {
            return RemoteType.HYBRID;
        }
        if (text.contains("homeoffice")
                || text.contains("home office")
                || text.contains("heimarbeit")
                || text.contains("telearbeit")
                || text.contains("\"ho\"")) {
            return RemoteType.REMOTE;
        }
        return summary.remoteType();
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
        if (hasText(criteria.getLocation())
                && !isBroadLocationFilter(criteria.getLocation())
                && hasText(offer.getLocation())
                && !offer.getLocation().toLowerCase(Locale.ROOT)
                .contains(criteria.getLocation().toLowerCase(Locale.ROOT))) {
            return false;
        }
        return true;
    }

    private boolean isGermany(JobSearchCriteria criteria) {
        if (criteria == null || !hasText(criteria.getCountry())) {
            return true;
        }
        String country = criteria.getCountry().trim().toLowerCase(Locale.ROOT);
        return country.equals("de")
                || country.equals("deutschland")
                || country.equals("germany");
    }

    private boolean isBroadLocationFilter(String location) {
        String normalized = location.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("de")
                || normalized.equals("deutschland")
                || normalized.equals("germany")
                || normalized.equals("germania")
                || normalized.equals("remote")
                || normalized.equals("homeoffice")
                || normalized.equals("home office")
                || normalized.equals("hybrid");
    }

    private Optional<Instant> parseDate(String value) {
        if (!hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value.trim()).atStartOfDay(GERMANY).toInstant());
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    @SafeVarargs
    private Instant firstInstant(Optional<Instant>... values) {
        for (Optional<Instant> value : values) {
            if (value.isPresent()) {
                return value.get();
            }
        }
        return null;
    }

    private String textFromArbeitsort(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isArray()) {
            List<String> locations = new ArrayList<>();
            for (JsonNode item : node) {
                String location = textFromArbeitsort(item);
                if (hasText(location)) {
                    locations.add(location);
                }
            }
            return summarizeLocations(locations);
        }
        if (node.isObject()) {
            if (node.has("adresse")) {
                return textFromArbeitsort(node.path("adresse"));
            }
            String ort = node.path("ort").asText("");
            String region = node.path("region").asText("");
            String land = node.path("land").asText("");
            if (hasText(ort) && hasText(region) && !ort.equalsIgnoreCase(region)) {
                return ort + ", " + region;
            }
            if (hasText(ort)) {
                return ort;
            }
            if (hasText(region)) {
                return region;
            }
            return land;
        }
        return node.asText("");
    }

    private String cleanDescription(String value) {
        return Jsoup.parse(value == null ? "" : value).text();
    }

    private String summarizeLocations(List<String> locations) {
        List<String> distinctLocations = locations.stream()
                .distinct()
                .toList();
        if (distinctLocations.size() <= 3) {
            return String.join(", ", distinctLocations);
        }
        return String.join(", ", distinctLocations.subList(0, 3))
                + " (+" + (distinctLocations.size() - 3) + " weitere)";
    }

    private String jobDetailUrl(String referenceNumber) {
        return UriComponentsBuilder.fromUriString(properties.getWebsiteBaseUrl())
                .path("/jobsuche/jobdetail/")
                .pathSegment(referenceNumber)
                .build()
                .encode()
                .toUriString();
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.asText("");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record JobSummary(String referenceNumber, String title, String company, String location, String description,
                      String externalUrl, Instant publishedAt, RemoteType remoteType, ContractType contractType) {
    }
}
