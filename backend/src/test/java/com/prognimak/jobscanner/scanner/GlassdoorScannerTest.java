package com.prognimak.jobscanner.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import com.prognimak.jobscanner.config.JobScannerProperties;
import com.prognimak.jobscanner.entity.JobOffer;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import com.prognimak.jobscanner.entity.RemoteType;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GlassdoorScannerTest {

    private final GlassdoorScanner scanner = new GlassdoorScanner(RestClient.builder(), new JobScannerProperties());

    @Test
    void buildsSeoSearchUrlBeforeQueryUrlForKeywordOnlySearch() {
        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setKeyword("Java");

        var urls = scanner.buildSearchUrls(criteria, 1);

        assertThat(urls).containsExactly(
                "https://www.glassdoor.de/Job/java-jobs-SRCH_KO0,4.htm",
                "https://www.glassdoor.de/Job/jobs.htm?sc.keyword=Java"
        );
    }

    @Test
    void usesQueryUrlWhenLocationIsProvided() {
        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setKeyword("Java");
        criteria.setLocation("Köln");

        var urls = scanner.buildSearchUrls(criteria, 1);

        assertThat(urls).containsExactly(
                "https://www.glassdoor.de/Job/jobs.htm?sc.keyword=Java&locKeyword=K%C3%B6ln"
        );
    }

    @Test
    void extractsOffersFromSearchResultCards() {
        String html = """
                <html>
                  <body>
                    <ul>
                      <li data-test="jobListing">
                        <div data-test="employer-name">DEVK Versicherungen</div>
                        <a data-test="job-title" href="/job-listing/java-entwickler-cloud-devops-devk-JV.htm">
                          Java Entwickler Cloud DevOps (m/w/d)
                        </a>
                        <div data-test="location">Köln</div>
                        <div>Du bist Teil einer großen agilen Einheit mit Java, Spring Boot und Cloud.</div>
                        <div>65.000 € - 85.000 € (Arbeitgeberangabe)</div>
                      </li>
                    </ul>
                  </body>
                </html>
                """;
        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setKeyword("Java");

        var offers = scanner.extractOffersFromSearchPage(
                html,
                "https://www.glassdoor.de/Job/jobs.htm?sc.keyword=java",
                criteria
        );

        assertThat(offers).hasSize(1);
        JobOffer offer = offers.getFirst();
        assertThat(offer.getSource()).isEqualTo("glassdoor");
        assertThat(offer.getTitle()).isEqualTo("Java Entwickler Cloud DevOps (m/w/d)");
        assertThat(offer.getCompany()).isEqualTo("DEVK Versicherungen");
        assertThat(offer.getLocation()).isEqualTo("Köln");
        assertThat(offer.getJobUrl())
                .isEqualTo("https://www.glassdoor.de/job-listing/java-entwickler-cloud-devops-devk-JV.htm");
        assertThat(offer.getRateOrSalary()).isEqualByComparingTo(new BigDecimal("85000"));
    }

    @Test
    void parsesDetailPage() {
        String html = """
                <html>
                  <body>
                    <main>
                      <h1 data-test="job-title">Senior Java Developer:in</h1>
                      <div data-test="employer-name">STRABAG SE</div>
                      <div data-test="location">Köln</div>
                      <section data-test="jobDescriptionContent">
                        Senior Java Developer:in mit Spring Boot, REST APIs und hybrider Arbeit.
                      </section>
                    </main>
                  </body>
                </html>
                """;

        Optional<JobOffer> offer = scanner.parseDetail(
                html,
                "https://www.glassdoor.de/job-listing/senior-java-developer-strabag-JV.htm"
        );

        assertThat(offer).isPresent();
        assertThat(offer.get().getTitle()).isEqualTo("Senior Java Developer:in");
        assertThat(offer.get().getCompany()).isEqualTo("STRABAG SE");
        assertThat(offer.get().getLocation()).isEqualTo("Köln");
        assertThat(offer.get().getRemoteType()).isEqualTo(RemoteType.HYBRID);
        assertThat(offer.get().getDescription()).contains("Spring Boot");
    }

    @Test
    void rejectsJavascriptOnlyResultWhenSearchingJava() {
        String html = """
                <html>
                  <body>
                    <li data-test="jobListing">
                      <div data-test="employer-name">Frontend GmbH</div>
                      <a data-test="job-title" href="/job-listing/javascript-frontend-JV.htm">
                        JavaScript Frontend Entwickler
                      </a>
                      <div data-test="location">Remote</div>
                      <div>JavaScript, TypeScript, React und Vue.</div>
                    </li>
                  </body>
                </html>
                """;
        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setKeyword("Java");

        var offers = scanner.extractOffersFromSearchPage(
                html,
                "https://www.glassdoor.de/Job/jobs.htm?sc.keyword=java",
                criteria
        );

        assertThat(offers).isEmpty();
    }
}
