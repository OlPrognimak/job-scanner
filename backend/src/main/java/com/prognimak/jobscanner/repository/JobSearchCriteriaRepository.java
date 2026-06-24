package com.prognimak.jobscanner.repository;

import com.prognimak.jobscanner.entity.JobSearchCriteria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobSearchCriteriaRepository extends JpaRepository<JobSearchCriteria, Long> {

    List<JobSearchCriteria> findByActiveTrue();
}
