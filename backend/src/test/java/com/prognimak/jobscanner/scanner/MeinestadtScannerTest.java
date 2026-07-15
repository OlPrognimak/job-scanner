package com.prognimak.jobscanner.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import com.prognimak.jobscanner.config.JobScannerProperties;
import com.prognimak.jobscanner.entity.ContractType;
import com.prognimak.jobscanner.entity.JobOffer;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import com.prognimak.jobscanner.entity.RemoteType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class MeinestadtScannerTest {

    private final MeinestadtScanner scanner = new MeinestadtScanner(RestClient.builder(), new JobScannerProperties());

    @Test
    void buildsSearchUrlWithKeywordAndLocation() {
        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setKeyword("Java Spring");
        criteria.setLocation("Koeln");

        String url = scanner.buildSearchUrl(criteria, 2);

        assertThat(url).isEqualTo("https://jobs.meinestadt.de/koeln/suche?words=Java%20Spring&page=2");
    }

    @Test
    void extractsJobCardsFromSearchPage() {
        String html = """
                <ul>
                  <li class="m-resultListEntryJobScan">
                    <a class="m-resultListEntryJobScan__clickArea" href="https://jobs.meinestadt.de/koeln/premium?id=123"
                       data-mst="{&quot;mslayer_element_detail_start_date&quot;:&quot;09.07.2026&quot;}">
                      <h3 class="m-resultListEntryJobScan__headline">Senior Java Entwickler (m/w/d)</h3>
                    </a>
                    <div class="m-resultListEntryJobScan__company">Acme GmbH</div>
                    <div class="m-resultListEntryJobScan__location">Koeln</div>
                    <div class="m-resultListEntryJobScan__date">09.07.2026</div>
                    <div class="m-resultListEntryJobScan__chip">Homeoffice</div>
                  </li>
                </ul>
                """;

        var offers = scanner.extractOffersFromSearchPage(html, "https://jobs.meinestadt.de/deutschland");

        assertThat(offers).hasSize(1);
        JobOffer offer = offers.getFirst();
        assertThat(offer.getSource()).isEqualTo("meinestadt");
        assertThat(offer.getTitle()).isEqualTo("Senior Java Entwickler (m/w/d)");
        assertThat(offer.getCompany()).isEqualTo("Acme GmbH");
        assertThat(offer.getLocation()).isEqualTo("Koeln");
        assertThat(offer.getRemoteType()).isEqualTo(RemoteType.REMOTE);
        assertThat(offer.getContractType()).isEqualTo(ContractType.PERMANENT);
        assertThat(offer.getJobUrl()).isEqualTo("https://jobs.meinestadt.de/koeln/premium?id=123");
        assertThat(offer.getPublishedAt()).isEqualTo(Instant.parse("2026-07-08T22:00:00Z"));
    }

    @Test
    void extractsJobCardsFromEmbeddedResultJson() {
        String html = """
                <script>
                  var jsLibParamsIn = JSON.parse("{\\"results\\":{\\"items\\":[{\\"title\\":\\"Senior Java Entwickler (m/w/d)\\",\\"companyName\\":\\"Acme GmbH\\",\\"workCity\\":\\"Berlin\\",\\"workTime\\":\\"Vollzeit\\",\\"jobOfferType\\":\\"Festanstellung\\",\\"mslayerElementStartDate\\":\\"14.07.2026\\",\\"detailUrl\\":\\"https://www.meinestadt.de/deutschland/redirect/jobs-redirect?id=123\\",\\"seoTags\\":[{\\"tag\\":\\"Java\\"},{\\"tag\\":\\"Spring Boot\\"}]}]}}");
                </script>
                """;

        var offers = scanner.extractOffersFromSearchPage(html, "https://jobs.meinestadt.de/deutschland/suche?words=java");

        assertThat(offers).hasSize(1);
        JobOffer offer = offers.getFirst();
        assertThat(offer.getTitle()).isEqualTo("Senior Java Entwickler (m/w/d)");
        assertThat(offer.getCompany()).isEqualTo("Acme GmbH");
        assertThat(offer.getLocation()).isEqualTo("Berlin");
        assertThat(offer.getContractType()).isEqualTo(ContractType.PERMANENT);
        assertThat(offer.getDescription()).contains("Tags: Java, Spring Boot");
        assertThat(offer.getJobUrl()).isEqualTo("https://www.meinestadt.de/deutschland/redirect/jobs-redirect?id=123");
        assertThat(offer.getPublishedAt()).isEqualTo(Instant.parse("2026-07-13T22:00:00Z"));
    }

    @Test
    void mapsDetailPageWithDescriptionAndCompactDate() {
        JobOffer fallback = new JobOffer();
        fallback.setTitle("Senior Java Entwickler (m/w/d)");
        fallback.setCompany("Acme GmbH");
        fallback.setLocation("Koeln");
        fallback.setJobUrl("https://jobs.meinestadt.de/koeln/premium?id=123");
        String html = """
                <main>
                  <section class="ms-jobDetailHeader">
                    <h1>Senior Java Backend Entwickler</h1>
                    <div class="ms-jobDetailHeader__companyName">Acme GmbH</div>
                  </section>
                  <article class="ms-jobDetailStyledText">
                    <p>Java, Spring Boot und Homeoffice.</p>
                    <p>Adresse Koeln Job Art Vollzeit</p>
                  </article>
                  <button data-mst='{"mslayer_element_detail_start_date":"20260710"}'></button>
                </main>
                """;

        var offer = scanner.parseDetail(html, fallback.getJobUrl(), fallback).orElseThrow();

        assertThat(offer.getTitle()).isEqualTo("Senior Java Backend Entwickler");
        assertThat(offer.getCompany()).isEqualTo("Acme GmbH");
        assertThat(offer.getLocation()).isEqualTo("Koeln");
        assertThat(offer.getRemoteType()).isEqualTo(RemoteType.REMOTE);
        assertThat(offer.getDescription()).contains("Java, Spring Boot und Homeoffice.");
        assertThat(offer.getPublishedAt()).isEqualTo(Instant.parse("2026-07-09T22:00:00Z"));
    }
}
