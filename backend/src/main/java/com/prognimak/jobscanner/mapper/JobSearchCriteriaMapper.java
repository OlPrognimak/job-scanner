package com.prognimak.jobscanner.mapper;

import com.prognimak.jobscanner.dto.JobSearchCriteriaDto;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import org.springframework.stereotype.Component;

@Component
public class JobSearchCriteriaMapper {

    public JobSearchCriteriaDto toDto(JobSearchCriteria criteria) {
        return new JobSearchCriteriaDto(
                criteria.getId(),
                criteria.getName(),
                criteria.getKeyword(),
                criteria.getLocation(),
                criteria.getRemoteType(),
                criteria.getContractType(),
                criteria.getMinRate(),
                criteria.getLanguage(),
                criteria.getSourceWebsite(),
                criteria.isActive()
        );
    }

    public void updateEntity(JobSearchCriteriaDto dto, JobSearchCriteria criteria) {
        criteria.setName(dto.name());
        criteria.setKeyword(dto.keyword());
        criteria.setLocation(dto.location());
        criteria.setRemoteType(dto.remoteType());
        criteria.setContractType(dto.contractType());
        criteria.setMinRate(dto.minRate());
        criteria.setLanguage(dto.language());
        criteria.setSourceWebsite(dto.sourceWebsite());
        criteria.setActive(dto.active());
    }
}
