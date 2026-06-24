package com.prognimak.jobscanner.scanner;

import com.prognimak.jobscanner.entity.JobOffer;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import java.util.List;

public interface JobSourceScanner {

    String source();

    List<JobOffer> scan(JobSearchCriteria criteria);
}
