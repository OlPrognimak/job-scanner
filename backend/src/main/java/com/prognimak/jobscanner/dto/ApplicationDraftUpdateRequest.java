package com.prognimak.jobscanner.dto;

public record ApplicationDraftUpdateRequest(
        String anschreibenText,
        String cvFilePath,
        String cvDocumentId
) {
}
