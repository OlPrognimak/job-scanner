package com.prognimak.jobscanner.dto;

import java.util.List;

public record ScanResultDto(int importedCount, List<JobOfferDto> jobs, List<String> messages) {
}
