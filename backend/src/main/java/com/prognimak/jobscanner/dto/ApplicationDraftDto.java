package com.prognimak.jobscanner.dto;

import com.prognimak.jobscanner.entity.DraftStatus;
import java.time.Instant;

public record ApplicationDraftDto(
        Long id,
        Long jobOfferId,
        String anschreibenText,
        String cvFilePath,
        String cvDocumentId,
        Instant createdAt,
        Instant updatedAt,
        Instant sentAt,
        DraftStatus status
) {
}
