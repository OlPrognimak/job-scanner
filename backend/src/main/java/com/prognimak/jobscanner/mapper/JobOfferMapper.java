package com.prognimak.jobscanner.mapper;

import com.prognimak.jobscanner.dto.JobOfferDto;
import com.prognimak.jobscanner.entity.JobOffer;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface JobOfferMapper {

    JobOfferDto toDto(JobOffer jobOffer);
}
