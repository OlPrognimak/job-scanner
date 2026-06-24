package com.prognimak.jobscanner.scanner;

import com.prognimak.jobscanner.entity.ContractType;
import com.prognimak.jobscanner.entity.JobOffer;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import com.prognimak.jobscanner.entity.RemoteType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MockJobSourceScanner implements JobSourceScanner {

    @Override
    public String source() {
        return "mock";
    }

    @Override
    public List<JobOffer> scan(JobSearchCriteria criteria) {
        String keyword = criteria.getKeyword() == null || criteria.getKeyword().isBlank()
                ? "Java Spring Boot"
                : criteria.getKeyword();

        JobOffer first = createOffer(
                "mock",
                keyword + " Freelancer fuer Cloud Migration",
                "Acme Consulting GmbH",
                criteria.getLocation() == null ? "Berlin" : criteria.getLocation(),
                RemoteType.HYBRID,
                ContractType.FREELANCE,
                "Gesucht wird Unterstuetzung fuer eine Spring Boot Migration mit PostgreSQL, Kafka und REST APIs. "
                        + "Erfahrung mit agilen Teams, sauberer Dokumentation und produktionsnaher Umsetzung ist wichtig.",
                "https://example.test/jobs/spring-cloud-migration",
                new BigDecimal("850")
        );

        JobOffer second = createOffer(
                "mock",
                "Senior Java Engineer Backend Plattform",
                "TechWorks AG",
                "Remote",
                RemoteType.REMOTE,
                ContractType.PERMANENT,
                "Backend Rolle mit Java 21, Spring Boot, PostgreSQL und API Design. "
                        + "Der Schwerpunkt liegt auf stabilen Services, Wartbarkeit und Zusammenarbeit mit Produktteams.",
                "https://example.test/jobs/senior-java-platform",
                new BigDecimal("95000")
        );

        return List.of(first, second);
    }

    private JobOffer createOffer(String source, String title, String company, String location, RemoteType remoteType,
                                 ContractType contractType, String description, String jobUrl, BigDecimal rateOrSalary) {
        JobOffer offer = new JobOffer();
        offer.setSource(source);
        offer.setTitle(title);
        offer.setCompany(company);
        offer.setLocation(location);
        offer.setRemoteType(remoteType);
        offer.setContractType(contractType);
        offer.setDescription(description);
        offer.setJobUrl(jobUrl);
        offer.setDetectedAt(Instant.now());
        offer.setRateOrSalary(rateOrSalary);
        return offer;
    }
}
