package com.prognimak.jobscanner.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prognimak.jobscanner.config.JobScannerProperties;
import com.prognimak.jobscanner.entity.ContractType;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import com.prognimak.jobscanner.entity.RemoteType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ArbeitsagenturScannerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ArbeitsagenturScanner scanner = new ArbeitsagenturScanner(RestClient.builder(), new JobScannerProperties());

    @Test
    void buildsSearchUriFromCriteria() {
        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setKeyword("Java, Spring Boot");
        criteria.setLocation("Remote");
        criteria.setRemoteType(RemoteType.REMOTE);

        String uri = scanner.buildSearchUri(criteria, 1, 1).toString();

        assertThat(uri).contains("was=Java%20Spring%20Boot");
        assertThat(uri).doesNotContain("wo=Remote");
        assertThat(uri).contains("arbeitszeit=ho");
        assertThat(uri).contains("angebotsart=1");
    }

    @Test
    void buildsDetailUriWithBase64ReferenceNumber() {
        String uri = scanner.buildDetailUri("10001-1002716922-S").toString();

        assertThat(uri)
                .isEqualTo("https://rest.arbeitsagentur.de/jobboerse/jobsuche-service/pc/v4/jobdetails/MTAwMDEtMTAwMjcxNjkyMi1T");
    }

    @Test
    void mapsSearchSummaryAndPublicationDate() throws Exception {
        var result = objectMapper.readTree("""
                {
                  "referenznummer": "10001-1234567890-S",
                  "stellenangebotsTitel": "Senior Java Entwickler/in",
                  "firma": "Acme GmbH",
                  "datumErsteVeroeffentlichung": "2026-07-10",
                  "homeofficemoeglich": true,
                  "stellenlokationen": [{
                    "adresse": {
                      "ort": "Koeln",
                      "region": "Nordrhein-Westfalen",
                      "land": "Deutschland"
                    }
                  }]
                }
                """);

        var summary = scanner.mapSummary(result, 1).orElseThrow();

        assertThat(summary.referenceNumber()).isEqualTo("10001-1234567890-S");
        assertThat(summary.title()).isEqualTo("Senior Java Entwickler/in");
        assertThat(summary.company()).isEqualTo("Acme GmbH");
        assertThat(summary.location()).isEqualTo("Koeln, Nordrhein-Westfalen");
        assertThat(summary.contractType()).isEqualTo(ContractType.PERMANENT);
        assertThat(summary.remoteType()).isEqualTo(RemoteType.REMOTE);
        assertThat(summary.publishedAt()).isEqualTo(Instant.parse("2026-07-09T22:00:00Z"));
    }

    @Test
    void mapsDetailsToJobOffer() throws Exception {
        var summary = new ArbeitsagenturScanner.JobSummary(
                "10001-1234567890-S",
                "Java Entwickler/in",
                "Acme GmbH",
                "Berlin",
                "",
                "",
                Instant.parse("2026-07-09T22:00:00Z"),
                null,
                ContractType.PERMANENT);
        var details = objectMapper.readTree("""
                {
                  "stellenangebotsTitel": "Senior Java Backend Entwickler",
                  "firma": "Acme GmbH",
                  "stellenangebotsBeschreibung": "<p>Java, Spring Boot und Homeoffice.</p>",
                  "datumErsteVeroeffentlichung": "2026-07-12",
                  "homeofficemoeglich": true,
                  "stellenlokationen": [{
                    "adresse": {
                      "ort": "Berlin",
                      "land": "Deutschland"
                    }
                  }]
                }
                """);

        var offer = scanner.mapOffer(summary, details);

        assertThat(offer.getSource()).isEqualTo("arbeitsagentur");
        assertThat(offer.getTitle()).isEqualTo("Senior Java Backend Entwickler");
        assertThat(offer.getCompany()).isEqualTo("Acme GmbH");
        assertThat(offer.getLocation()).isEqualTo("Berlin");
        assertThat(offer.getRemoteType()).isEqualTo(RemoteType.REMOTE);
        assertThat(offer.getContractType()).isEqualTo(ContractType.PERMANENT);
        assertThat(offer.getDescription()).isEqualTo("Java, Spring Boot und Homeoffice.");
        assertThat(offer.getJobUrl()).isEqualTo("https://www.arbeitsagentur.de/jobsuche/jobdetail/10001-1234567890-S");
        assertThat(offer.getPublishedAt()).isEqualTo(Instant.parse("2026-07-11T22:00:00Z"));
    }
}
