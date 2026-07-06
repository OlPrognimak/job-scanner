package com.prognimak.jobscanner.mapper;

import com.prognimak.jobscanner.dto.JobSearchCriteriaDto;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface JobSearchCriteriaMapper {

    JobSearchCriteriaDto toDto(JobSearchCriteria criteria);

    @Mapping(target = "id", ignore = true)
    void updateEntity(JobSearchCriteriaDto dto, @MappingTarget JobSearchCriteria criteria);
}
