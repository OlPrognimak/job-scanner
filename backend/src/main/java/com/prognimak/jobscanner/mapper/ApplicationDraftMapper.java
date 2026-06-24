package com.prognimak.jobscanner.mapper;

import com.prognimak.jobscanner.dto.ApplicationDraftDto;
import com.prognimak.jobscanner.entity.ApplicationDraft;
import org.springframework.stereotype.Component;

@Component
public class ApplicationDraftMapper {

    public ApplicationDraftDto toDto(ApplicationDraft draft) {
        return new ApplicationDraftDto(
                draft.getId(),
                draft.getJobOffer().getId(),
                draft.getAnschreibenText(),
                draft.getCvFilePath(),
                draft.getCvDocumentId(),
                draft.getCreatedAt(),
                draft.getUpdatedAt(),
                draft.getSentAt(),
                draft.getStatus()
        );
    }
}
