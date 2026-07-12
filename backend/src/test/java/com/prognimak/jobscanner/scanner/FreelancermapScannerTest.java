package com.prognimak.jobscanner.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import com.prognimak.jobscanner.config.JobScannerProperties;
import com.prognimak.jobscanner.entity.JobOffer;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import com.prognimak.jobscanner.entity.RemoteType;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class FreelancermapScannerTest {

    private final FreelancermapScanner scanner = new FreelancermapScanner(
            RestClient.builder(),
            testProperties()
    );

    @Test
    void extractsProjectDetailUrls() {
        String html = """
                <html>
                  <body>
                    <div class="project-card">
                      <div class="project-info">
                        <div>Acme GmbH</div>
                        <div>
                          <a data-testid="title" data-id="project-card-title" href="/projekt/java-spring-boot-entwickler">
                            Java Spring Boot Entwickler
                          </a>
                        </div>
                        <div class="project-info-list">
                          <div data-testid="city">Berlin, Deutschland</div>
                          <div data-testid="remoteInPercent">100% Remote</div>
                          <div data-testid="type">Freiberuflich</div>
                        </div>
                      </div>
                    </div>
                    <a href="/projektanbieter">Projektanbieter</a>
                    <a href="/projektboerse/projekte">Projektboerse</a>
                  </body>
                </html>
                """;

        Set<String> urls = scanner.extractDetailUrls(html, "https://www.freelancermap.de/projekte?query=java");

        assertThat(urls).containsExactly("https://www.freelancermap.de/projekt/java-spring-boot-entwickler");
    }

    @Test
    void extractsOffersFromSearchResultCards() {
        String html = """
                <html>
                  <body>
                    <div class="project-card">
                      <div class="project-info">
                        <div>Hays AG</div>
                        <div>
                          <a data-testid="title" data-id="project-card-title" href="/projekt/backend-entwickler-java-spring-boot">
                            Backend-Entwickler Java Spring Boot
                          </a>
                        </div>
                        <div class="project-info-list">
                          <div data-testid="city">Remote</div>
                          <div data-testid="remoteInPercent">100% Remote</div>
                          <div data-testid="type">Freiberuflich</div>
                        </div>
                      </div>
                    </div>
                  </body>
                </html>
                """;
        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setKeyword("Java");

        var offers = scanner.extractOffersFromSearchPage(html, "https://www.freelancermap.de/projekte?query=java", criteria);

        assertThat(offers).hasSize(1);
        assertThat(offers.getFirst().getTitle()).isEqualTo("Backend-Entwickler Java Spring Boot");
        assertThat(offers.getFirst().getCompany()).isEqualTo("Hays AG");
        assertThat(offers.getFirst().getJobUrl())
                .isEqualTo("https://www.freelancermap.de/projekt/backend-entwickler-java-spring-boot");
    }

    @Test
    void keepsSearchResultCardBeforeDetailContentMatching() {
        String html = """
                <html>
                  <body>
                    <div class="project-card">
                      <div class="project-info">
                        <div>Hays AG</div>
                        <div>
                          <a data-testid="title" data-id="project-card-title" href="/projekt/java-entwickler">
                            Java Entwickler
                          </a>
                        </div>
                        <div class="project-info-list">
                          <div data-testid="remoteInPercent">100% Remote</div>
                          <div data-testid="type">Freiberuflich</div>
                        </div>
                      </div>
                    </div>
                  </body>
                </html>
                """;
        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setKeyword("Java Spring Boot");

        var offers = scanner.extractOffersFromSearchPage(html, "https://www.freelancermap.de/projekte?query=java", criteria);

        assertThat(offers).hasSize(1);
    }

    @Test
    void parsesProjectDetailPage() {
        String html = """
                <html>
                  <body>
                    <script>
                      window.__DATA__ = {"project":{"company":"SThree GmbH","country":{"localizedName":"Deutschland"},"city":"M\\u00fcnchen"}};
                    </script>
                    <main>
                      <h1 class="h2 mg-b-display-m">Senior Java Spring Boot Entwickler</h1>
                      <section class="project-description">
                        Gesucht wird Unterstuetzung mit Java 21, Spring Boot, PostgreSQL und Kafka.
                        Remote Projekt mit 850 EUR / Tag.
                      </section>
                    </main>
                  </body>
                </html>
                """;

        Optional<JobOffer> offer = scanner.parseDetail(
                html,
                "https://www.freelancermap.de/projekt/java-spring-boot-entwickler?utm=ignored"
        );

        assertThat(offer).isPresent();
        assertThat(offer.get().getSource()).isEqualTo("freelancermap");
        assertThat(offer.get().getTitle()).isEqualTo("Senior Java Spring Boot Entwickler");
        assertThat(offer.get().getCompany()).isEqualTo("SThree GmbH");
        assertThat(offer.get().getLocation()).isEqualTo("München, Deutschland");
        assertThat(offer.get().getRemoteType()).isEqualTo(RemoteType.REMOTE);
        assertThat(offer.get().getJobUrl())
                .isEqualTo("https://www.freelancermap.de/projekt/java-spring-boot-entwickler");
        assertThat(offer.get().getDescription()).contains("Java 21");
    }

    @Test
    void rejectsGenericPortalPage() {
        String html = """
                <html>
                  <body>
                    <main>
                      <h1>Finden Sie das passende Projekt</h1>
                      <p>Vor Ort Remote Hybrid</p>
                    </main>
                  </body>
                </html>
                """;

        Optional<JobOffer> offer = scanner.parseDetail(
                html,
                "https://www.freelancermap.de/projektboerse/projekte"
        );

        assertThat(offer).isEmpty();
    }

    @Test
    void acceptsDetailPageEvenWhenKeywordDoesNotMatchLocally() {
        String html = """
                <html>
                  <body>
                    <main>
                      <h1>SAP Beratung FI CO</h1>
                      <section class="project-description">Gesucht wird SAP FI CO Beratung.</section>
                    </main>
                  </body>
                </html>
                """;
        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setKeyword("Java");

        Optional<JobOffer> offer = scanner.parseDetail(
                html,
                "https://www.freelancermap.de/projekt/sap-beratung",
                criteria
        );

        assertThat(offer).isPresent();
        assertThat(offer.get().getTitle()).isEqualTo("SAP Beratung FI CO");
    }

    @Test
    void acceptsDetailPageWhenFreelancermapSearchReturnedIt() {
        String html = """
                <html>
                  <body>
                    <main>
                      <h1>Legacy Frontend Modernisierung</h1>
                      <section class="project-description">
                        Gesucht wird Unterstuetzung fuer eine Migration aus GWT in eine moderne Webplattform.
                      </section>
                    </main>
                  </body>
                </html>
                """;
        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setKeyword("gwt");

        Optional<JobOffer> offer = scanner.parseDetail(
                html,
                "https://www.freelancermap.de/projekt/legacy-frontend-modernisierung",
                criteria
        );

        assertThat(offer).isPresent();
        assertThat(offer.get().getTitle()).isEqualTo("Legacy Frontend Modernisierung");
        assertThat(offer.get().getDescription()).contains("GWT");
    }

    @Test
    void rejectsJavascriptOnlyResultWhenSearchingJava() {
        String html = """
                <html>
                  <body>
                    <main>
                      <h1 class="h2 mg-b-display-m">JavaScript Frontend Entwickler</h1>
                      <section class="project-description">
                        Gesucht wird Erfahrung mit JavaScript, TypeScript, React und Vue.
                      </section>
                    </main>
                  </body>
                </html>
                """;
        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setKeyword("Java");

        Optional<JobOffer> offer = scanner.parseDetail(
                html,
                "https://www.freelancermap.de/projekt/javascript-frontend-entwickler",
                criteria
        );

        assertThat(offer).isEmpty();
    }

    @Test
    void acceptsStandaloneJavaWhenSearchingJava() {
        String html = """
                <html>
                  <body>
                    <main>
                      <h1 class="h2 mg-b-display-m">Java Backend Entwickler</h1>
                      <section class="project-description">
                        Gesucht wird Erfahrung mit Java, Spring Boot und PostgreSQL.
                      </section>
                    </main>
                  </body>
                </html>
                """;
        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setKeyword("Java");

        Optional<JobOffer> offer = scanner.parseDetail(
                html,
                "https://www.freelancermap.de/projekt/java-backend-entwickler",
                criteria
        );

        assertThat(offer).isPresent();
    }

    private static JobScannerProperties testProperties() {
        JobScannerProperties properties = new JobScannerProperties();
        properties.getScanners().getFreelancermap().setRequestDelayMs(0);
        return properties;
    }
}
