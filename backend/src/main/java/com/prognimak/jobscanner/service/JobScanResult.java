package com.prognimak.jobscanner.service;

import com.prognimak.jobscanner.entity.JobOffer;
import java.util.List;

public record JobScanResult(List<JobOffer> jobs, List<String> messages) {
}
