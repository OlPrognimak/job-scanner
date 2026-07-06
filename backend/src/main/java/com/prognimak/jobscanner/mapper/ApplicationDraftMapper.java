package com.prognimak.jobscanner.mapper;

import com.prognimak.jobscanner.dto.ApplicationDraftDto;
import com.prognimak.jobscanner.entity.ApplicationDraft;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ApplicationDraftMapper {

    @Mapping(target = "jobOfferId", source = "jobOffer.id")
    ApplicationDraftDto toDto(ApplicationDraft draft);
}
