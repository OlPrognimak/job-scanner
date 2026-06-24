package com.prognimak.jobscanner.dto;

import com.prognimak.jobscanner.entity.ContractType;
import com.prognimak.jobscanner.entity.RemoteType;
import java.math.BigDecimal;

public record JobSearchCriteriaDto(
        Long id,
        String name,
        String keyword,
        String location,
        RemoteType remoteType,
        ContractType contractType,
        BigDecimal minRate,
        String language,
        String sourceWebsite,
        boolean active
) {
}
