package com.prognimak.jobscanner.scanner;

import com.prognimak.jobscanner.entity.JobOffer;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FreelancermapScanner implements JobSourceScanner {

    @Override
    public String source() {
        return "freelancermap";
    }

    @Override
    public List<JobOffer> scan(JobSearchCriteria criteria) {
        // Skeleton for a later real adapter. Keep parsing, rate limiting and source-specific policy here.
        return List.of();
    }
}
