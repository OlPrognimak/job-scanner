package com.prognimak.jobscanner.dto;

import com.prognimak.jobscanner.entity.ContractType;
import com.prognimak.jobscanner.entity.JobStatus;
import com.prognimak.jobscanner.entity.RemoteType;
import java.math.BigDecimal;
import java.time.Instant;

public record JobOfferDto(
        Long id,
        String source,
        String sourceJobId,
        String title,
        String company,
        String location,
        RemoteType remoteType,
        ContractType contractType,
        String description,
        String jobUrl,
        Instant detectedAt,
        Instant publishedAt,
        JobStatus status,
        Integer matchScore,
        String matchExplanation,
        BigDecimal rateOrSalary
) {
}
