package com.prognimak.jobscanner.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prognimak.jobscanner.config.JobScannerProperties;
import com.prognimak.jobscanner.entity.ContractType;
import com.prognimak.jobscanner.entity.RemoteType;
import java.math.BigDecimal;
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
        assertThat(offer.getRateOrSalary()).isEqualByComparingTo(new BigDecimal("95000"));
    }
}
