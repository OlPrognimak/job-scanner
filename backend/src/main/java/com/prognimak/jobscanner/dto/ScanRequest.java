package com.prognimak.jobscanner.dto;

public record ScanRequest(
        Long criteriaId,
        String keyword,
        String country,
        String location,
        String sourceWebsite
) {
}
