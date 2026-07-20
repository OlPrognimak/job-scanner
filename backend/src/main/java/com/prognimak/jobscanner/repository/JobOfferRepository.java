package com.prognimak.jobscanner.repository;

import com.prognimak.jobscanner.entity.JobOffer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobOfferRepository extends JpaRepository<JobOffer, Long> {

    Optional<JobOffer> findByJobUrl(String jobUrl);

    Optional<JobOffer> findBySourceAndSourceJobId(String source, String sourceJobId);
}
