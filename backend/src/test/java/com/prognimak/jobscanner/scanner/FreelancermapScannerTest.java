package com.prognimak.jobscanner.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import com.prognimak.jobscanner.config.JobScannerProperties;
import com.prognimak.jobscanner.entity.JobOffer;
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
                    <a href="/projekt/java-spring-boot-entwickler">Java Projekt</a>
                    <a href="/freelancer/some-profile">Freelancer</a>
                    <a href="/projektboerse.html?query=java">Search</a>
                  </body>
                </html>
                """;

        Set<String> urls = scanner.extractDetailUrls(html, "https://www.freelancermap.de/projektboerse.html");

        assertThat(urls).containsExactly("https://www.freelancermap.de/projekt/java-spring-boot-entwickler");
    }

    @Test
    void parsesProjectDetailPage() {
        String html = """
                <html>
                  <body>
                    <main>
                      <h1>Senior Java Spring Boot Entwickler | freelancermap</h1>
                      <div class="company">Acme GmbH</div>
                      <div class="location">Remote</div>
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
        assertThat(offer.get().getCompany()).isEqualTo("Acme GmbH");
        assertThat(offer.get().getLocation()).isEqualTo("Remote");
        assertThat(offer.get().getRemoteType()).isEqualTo(RemoteType.REMOTE);
        assertThat(offer.get().getJobUrl())
                .isEqualTo("https://www.freelancermap.de/projekt/java-spring-boot-entwickler");
        assertThat(offer.get().getDescription()).contains("Java 21");
    }

    private static JobScannerProperties testProperties() {
        JobScannerProperties properties = new JobScannerProperties();
        properties.getScanners().getFreelancermap().setRequestDelayMs(0);
        return properties;
    }
}
