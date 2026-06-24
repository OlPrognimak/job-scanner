package com.prognimak.jobscanner.mapper;

import com.prognimak.jobscanner.dto.JobOfferDto;
import com.prognimak.jobscanner.entity.JobOffer;
import org.springframework.stereotype.Component;

@Component
public class JobOfferMapper {

    public JobOfferDto toDto(JobOffer jobOffer) {
        return new JobOfferDto(
                jobOffer.getId(),
                jobOffer.getSource(),
                jobOffer.getTitle(),
                jobOffer.getCompany(),
                jobOffer.getLocation(),
                jobOffer.getRemoteType(),
                jobOffer.getContractType(),
                jobOffer.getDescription(),
                jobOffer.getJobUrl(),
                jobOffer.getDetectedAt(),
                jobOffer.getStatus(),
                jobOffer.getMatchScore(),
                jobOffer.getMatchExplanation(),
                jobOffer.getRateOrSalary()
        );
    }
}
