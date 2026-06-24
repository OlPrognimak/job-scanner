package com.prognimak.jobscanner.service;

import com.prognimak.jobscanner.dto.JobSearchCriteriaDto;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import com.prognimak.jobscanner.mapper.JobSearchCriteriaMapper;
import com.prognimak.jobscanner.repository.JobSearchCriteriaRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchCriteriaService {

    private final JobSearchCriteriaRepository repository;
    private final JobSearchCriteriaMapper mapper;

    public SearchCriteriaService(JobSearchCriteriaRepository repository, JobSearchCriteriaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<JobSearchCriteria> findAll() {
        return repository.findAll();
    }

    @Transactional
    public JobSearchCriteria create(JobSearchCriteriaDto dto) {
        JobSearchCriteria criteria = new JobSearchCriteria();
        mapper.updateEntity(dto, criteria);
        return repository.save(criteria);
    }

    @Transactional
    public JobSearchCriteria update(Long id, JobSearchCriteriaDto dto) {
        JobSearchCriteria criteria = repository.findById(id).orElseThrow();
        mapper.updateEntity(dto, criteria);
        return repository.save(criteria);
    }
}
