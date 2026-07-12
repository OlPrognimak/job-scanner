package com.prognimak.jobscanner.scanner;

import com.fasterxml.jackson.databind.JsonNode;
import com.prognimak.jobscanner.config.JobScannerProperties;
import com.prognimak.jobscanner.entity.ContractType;
import com.prognimak.jobscanner.entity.JobOffer;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import com.prognimak.jobscanner.entity.RemoteType;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
public class AdzunaScanner implements JobSourceScanner {

    private static final Logger log = LoggerFactory.getLogger(AdzunaScanner.class);

    private final RestClient restClient;
    private final JobScannerProperties.Adzuna properties;

    public AdzunaScanner(RestClient.Builder restClientBuilder, JobScannerProperties properties) {
        this.properties = properties.getScanners().getAdzuna();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(1, this.properties.getRequestTimeoutSeconds()));
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Override
    public String source() {
        return "adzuna";
    }

    @Override
    public List<JobOffer> scan(JobSearchCriteria criteria) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        if (properties.getAppId() == null || properties.getAppId().isBlank()
                || properties.getAppKey() == null || properties.getAppKey().isBlank()) {
            throw new ScannerBlockedException(source(),
                    "Adzuna API credentials are missing. Set ADZUNA_APP_ID and ADZUNA_APP_KEY.",
                    null);
        }

        List<JobOffer> offers = new ArrayList<>();
        long startedAt = System.nanoTime();
        for (int page = 1; page <= Math.max(1, properties.getMaxPages()); page++) {
            log.info("Adzuna scan page {} started: keyword={}, location={}, country={}",
                    page, criteria.getKeyword(), criteria.getLocation(), country(criteria));
            JsonNode response = fetch(criteria, page);
            if (response == null) {
                break;
            }
            JsonNode results = response.path("results");
            if (!results.isArray() || results.isEmpty()) {
                break;
            }
            for (JsonNode result : results) {
                JobOffer offer = mapOffer(result);
                if (offer.getTitle().isBlank() || offer.getJobUrl().isBlank()) {
                    continue;
                }
                if (matchesLocalCriteria(offer, criteria)) {
                    offers.add(offer);
                }
            }
        }
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("Adzuna scan finished: imported={}, elapsedMs={}", offers.size(), elapsedMs);
        return offers;
    }

    private JsonNode fetch(JobSearchCriteria criteria, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/v1/api/jobs/")
                .path(country(criteria))
                .path("/search/")
                .path(String.valueOf(page))
                .queryParam("app_id", properties.getAppId())
                .queryParam("app_key", properties.getAppKey())
                .queryParam("results_per_page", Math.max(1, properties.getResultsPerPage()))
                .queryParam("content-type", "application/json");

        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            builder.queryParam("what", criteria.getKeyword());
        }
        if (criteria.getLocation() != null && !criteria.getLocation().isBlank()
                && !isCountryOnlyLocation(criteria.getLocation())) {
            builder.queryParam("where", criteria.getLocation());
        }
        if (criteria.getMinRate() != null) {
            builder.queryParam("salary_min", criteria.getMinRate());
        }
        if (criteria.getContractType() == ContractType.PERMANENT) {
            builder.queryParam("permanent", 1);
        }

        try {
            return restClient.get()
                    .uri(URI.create(builder.build().encode().toUriString()))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
            throw new ScannerBlockedException(source(),
                    "Adzuna API request was rejected. Check ADZUNA_APP_ID and ADZUNA_APP_KEY.",
                    ex);
        } catch (RestClientException ex) {
            throw new ScannerBlockedException(source(),
                    "Adzuna API request failed or timed out after "
                            + properties.getRequestTimeoutSeconds()
                            + " seconds. Check credentials, network and ADZUNA_COUNTRY.",
                    ex);
        }
    }

    JobOffer mapOffer(JsonNode result) {
        JobOffer offer = new JobOffer();
        offer.setSource(source());
        offer.setTitle(text(result, "title"));
        offer.setCompany(text(result.path("company"), "display_name"));
        offer.setLocation(text(result.path("location"), "display_name"));
        offer.setRemoteType(detectRemoteType(result.path("description").asText("")));
        offer.setContractType(detectContractType(result));
        offer.setDescription(cleanDescription(result.path("description").asText("")));
        offer.setJobUrl(result.path("redirect_url").asText(""));
        offer.setDetectedAt(parseInstant(result.path("created").asText("")));
        offer.setRateOrSalary(extractSalary(result));
        return offer;
    }

    private ContractType detectContractType(JsonNode result) {
        String contractType = result.path("contract_type").asText("").toLowerCase(Locale.ROOT);
        if (contractType.contains("permanent")) {
            return ContractType.PERMANENT;
        }
        if (contractType.contains("contract") || contractType.contains("freelance")) {
            return ContractType.FREELANCE;
        }
        return ContractType.PERMANENT;
    }

    private RemoteType detectRemoteType(String description) {
        String lower = description.toLowerCase(Locale.ROOT);
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

    private BigDecimal extractSalary(JsonNode result) {
        if (result.hasNonNull("salary_max")) {
            return result.path("salary_max").decimalValue();
        }
        if (result.hasNonNull("salary_min")) {
            return result.path("salary_min").decimalValue();
        }
        return null;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            return Instant.now();
        }
    }

    private String cleanDescription(String description) {
        return Jsoup.parse(description == null ? "" : description).text();
    }

    private String text(JsonNode node, String fieldName) {
        return node.path(fieldName).asText("");
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
        return true;
    }

    private String country(JobSearchCriteria criteria) {
        if (criteria.getCountry() != null && !criteria.getCountry().isBlank()) {
            return criteria.getCountry().trim().toLowerCase(Locale.ROOT);
        }
        return properties.getCountry();
    }

    private boolean isCountryOnlyLocation(String location) {
        String normalized = location.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("de")
                || normalized.equals("deutschland")
                || normalized.equals("germany")
                || normalized.equals("germania");
    }
}
