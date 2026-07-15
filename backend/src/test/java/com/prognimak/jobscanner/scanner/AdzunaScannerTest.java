package com.prognimak.jobscanner.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prognimak.jobscanner.config.JobScannerProperties;
import com.prognimak.jobscanner.entity.ContractType;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import com.prognimak.jobscanner.entity.RemoteType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AdzunaScannerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdzunaScanner scanner = new AdzunaScanner(RestClient.builder(), new JobScannerProperties());

    @Test
    void mapsAdzunaJobResult() throws Exception {
        var result = objectMapper.readTree("""
                {
                  "id": "123",
                  "title": "Senior Java Developer",
                  "company": { "display_name": "Acme GmbH" },
                  "location": { "display_name": "Koeln" },
                  "description": "Java, Spring Boot und <strong>hybrid</strong> work.",
                  "redirect_url": "https://www.adzuna.de/jobs/land/ad/123",
                  "created": "2026-07-12T10:15:30Z",
                  "salary_min": 75000,
                  "salary_max": 95000,
                  "contract_type": "permanent"
                }
                """);

        var offer = scanner.mapOffer(result);

        assertThat(offer.getSource()).isEqualTo("adzuna");
        assertThat(offer.getTitle()).isEqualTo("Senior Java Developer");
        assertThat(offer.getCompany()).isEqualTo("Acme GmbH");
        assertThat(offer.getLocation()).isEqualTo("Koeln");
        assertThat(offer.getRemoteType()).isEqualTo(RemoteType.HYBRID);
        assertThat(offer.getContractType()).isEqualTo(ContractType.PERMANENT);
        assertThat(offer.getDescription()).isEqualTo("Java, Spring Boot und hybrid work.");
        assertThat(offer.getJobUrl()).isEqualTo("https://www.adzuna.de/jobs/land/ad/123");
        assertThat(offer.getPublishedAt()).isEqualTo(java.time.Instant.parse("2026-07-12T10:15:30Z"));
        assertThat(offer.getRateOrSalary()).isEqualByComparingTo(new BigDecimal("95000"));
    }

    @Test
    void keepsContractTypeUnknownWhenAdzunaDoesNotProvideIt() throws Exception {
        var result = objectMapper.readTree("""
                {
                  "title": "Java Developer",
                  "company": { "display_name": "Acme GmbH" },
                  "location": { "display_name": "Germany" },
                  "description": "Java and Spring Boot",
                  "redirect_url": "https://www.adzuna.de/jobs/land/ad/456"
                }
                """);

        var offer = scanner.mapOffer(result);

        assertThat(offer.getContractType()).isNull();
    }

    @Test
    void doesNotSendRemoteAsWhereLocationToAdzuna() {
        JobScannerProperties properties = new JobScannerProperties();
        properties.getScanners().getAdzuna().setAppId("app-id");
        properties.getScanners().getAdzuna().setAppKey("app-key");
        AdzunaScanner scanner = new AdzunaScanner(RestClient.builder(), properties);

        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setKeyword("java");
        criteria.setLocation("Remote");
        criteria.setCountry("de");

        String uri = scanner.buildSearchUri(criteria, 1).toString();

        assertThat(uri).contains("what=java");
        assertThat(uri).doesNotContain("where=Remote");
    }

    @Test
    void sendsCommaSeparatedSkillsAsOrQueryToAdzuna() {
        JobScannerProperties properties = new JobScannerProperties();
        properties.getScanners().getAdzuna().setAppId("app-id");
        properties.getScanners().getAdzuna().setAppKey("app-key");
        AdzunaScanner scanner = new AdzunaScanner(RestClient.builder(), properties);

        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setKeyword("Java, Spring Boot, Kafka");
        criteria.setCountry("de");

        String uri = scanner.buildSearchUri(criteria, 1).toString();

        assertThat(uri).contains("what_or=Java%20Spring%20Boot%20Kafka");
        assertThat(uri).doesNotContain("what=Java");
    }

    @Test
    void liveJavaSearchReturnsResultsWhenAdzunaCredentialsArePresent() {
        String appId = System.getenv("ADZUNA_APP_ID");
        String appKey = System.getenv("ADZUNA_APP_KEY");
        Assumptions.assumeTrue(appId != null && !appId.isBlank(), "ADZUNA_APP_ID is not set");
        Assumptions.assumeTrue(appKey != null && !appKey.isBlank(), "ADZUNA_APP_KEY is not set");

        JobScannerProperties properties = new JobScannerProperties();
        properties.getScanners().getAdzuna().setAppId(appId);
        properties.getScanners().getAdzuna().setAppKey(appKey);
        properties.getScanners().getAdzuna().setCountry("de");
        properties.getScanners().getAdzuna().setResultsPerPage(10);
        properties.getScanners().getAdzuna().setMaxPages(1);

        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setKeyword("java");
        criteria.setCountry("de");

        var offers = new AdzunaScanner(RestClient.builder(), properties).scan(criteria);

        assertThat(offers)
                .as("Live Adzuna search for java in de should return at least one importable result")
                .isNotEmpty();
    }
}
