package com.prognimak.jobscanner.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import com.prognimak.jobscanner.config.JobScannerProperties;
import com.prognimak.jobscanner.entity.JobOffer;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import com.prognimak.jobscanner.entity.RemoteType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    void extractsFreelancermapProjectIdFromEmbeddedSearchState() {
        String html = """
                <html>
                  <body>
                    <script type="application/json" class="js-react-on-rails-component">
                      {"initialState":{"result":{"projects":[
                        {"id":3024633,"slug":"backend-entwickler-java-spring-boot","title":"Backend-Entwickler Java Spring Boot"}
                      ]}}}
                    </script>
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

        var offers = scanner.extractOffersFromSearchPage(html, "https://www.freelancermap.de/projekte?query=java", null);

        assertThat(offers).hasSize(1);
        assertThat(offers.getFirst().getSourceJobId()).isEqualTo("3024633");
    }

    @Test
    void extractsOffersFromAjaxProjectResults() {
        String json = """
                {
                  "projects": [
                    {
                      "id": 3024633,
                      "slug": "backend-entwickler-java-spring-boot",
                      "title": "Backend-Entwickler Java Spring Boot",
                      "company": "Hays AG",
                      "city": "Koeln",
                      "country": {"nameDe": "Deutschland"},
                      "created": "2026-07-10T13:31:00+02:00",
                      "description": "<div>Java, Spring Boot und Kafka.</div>",
                      "projectContractType": {"type": "contracting", "remoteInPercent": 100},
                      "links": {"project": "/projekt/backend-entwickler-java-spring-boot"},
                      "skills": [{"de": "Java"}, {"de": "Spring Boot"}]
                    }
                  ],
                  "currentPage": 2
                }
                """;
        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setKeyword("Java");

        var offers = scanner.extractOffersFromAjaxResult(json, "https://www.freelancermap.de/project/search/ajax", criteria);

        assertThat(offers).hasSize(1);
        JobOffer offer = offers.getFirst();
        assertThat(offer.getSourceJobId()).isEqualTo("3024633");
        assertThat(offer.getTitle()).isEqualTo("Backend-Entwickler Java Spring Boot");
        assertThat(offer.getCompany()).isEqualTo("Hays AG");
        assertThat(offer.getLocation()).isEqualTo("Koeln, Deutschland");
        assertThat(offer.getRemoteType()).isEqualTo(RemoteType.REMOTE);
        assertThat(offer.getJobUrl()).isEqualTo("https://www.freelancermap.de/projekt/backend-entwickler-java-spring-boot");
        assertThat(offer.getDescription()).contains("Java, Spring Boot und Kafka.", "Skills: Java, Spring Boot");
        assertThat(offer.getPublishedAt()).isEqualTo(java.time.OffsetDateTime
                .parse("2026-07-10T13:31:00+02:00")
                .toInstant());
    }

    @Test
    void extractsOriginalCreatedDateFromSearchResultCard() {
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
                        <div class="project-created"><span data-testid="created" class="created">10.07.2026</span></div>
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
        assertThat(offers.getFirst().getPublishedAt())
                .isEqualTo(LocalDate.of(2026, 7, 10).atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant());
    }

    @Test
    void extractsCurrentDateWithTimeFromSearchResultCard() {
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
                        <div class="project-created"><span data-testid="created" class="created">10:44</span></div>
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
        assertThat(offers.getFirst().getPublishedAt())
                .isEqualTo(LocalDate.now(ZoneId.of("Europe/Berlin"))
                        .atTime(10, 44)
                        .atZone(ZoneId.of("Europe/Berlin"))
                        .toInstant());
    }

    @Test
    void extractsOriginalPublishedDateTimeFromDetailHeader() {
        String html = """
                <html>
                  <body>
                    <main>
                      <div class="project-show-header">
                        <p class="color-text-display-weak line-height-base">
                          veröffentlicht am 09.07.2026, 15:15&nbsp;Uhr
                        </p>
                      </div>
                      <h1 class="h2 mg-b-display-m">Backend Softwareentwicklung Microservices</h1>
                      <section class="project-description">Java, Spring Boot und React.</section>
                    </main>
                  </body>
                </html>
                """;

        Optional<JobOffer> offer = scanner.parseDetail(
                html,
                "https://www.freelancermap.de/projekt/java-full-stack-developer"
        );

        assertThat(offer).isPresent();
        assertThat(offer.get().getTitle()).isEqualTo("Backend Softwareentwicklung Microservices");
        assertThat(offer.get().getPublishedAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 9, 15, 15)
                        .atZone(ZoneId.of("Europe/Berlin"))
                        .toInstant());
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
                      <meta itemprop="datePosted" content="2026-07-11T08:30:00Z" />
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
        assertThat(offer.get().getPublishedAt()).isEqualTo(java.time.Instant.parse("2026-07-11T08:30:00Z"));
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
