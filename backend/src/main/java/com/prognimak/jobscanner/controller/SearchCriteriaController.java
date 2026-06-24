package com.prognimak.jobscanner.controller;

import com.prognimak.jobscanner.dto.JobSearchCriteriaDto;
import com.prognimak.jobscanner.mapper.JobSearchCriteriaMapper;
import com.prognimak.jobscanner.service.SearchCriteriaService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search-criteria")
public class SearchCriteriaController {

    private final SearchCriteriaService service;
    private final JobSearchCriteriaMapper mapper;

    public SearchCriteriaController(SearchCriteriaService service, JobSearchCriteriaMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public List<JobSearchCriteriaDto> list() {
        return service.findAll().stream().map(mapper::toDto).toList();
    }

    @PostMapping
    public JobSearchCriteriaDto create(@RequestBody JobSearchCriteriaDto dto) {
        return mapper.toDto(service.create(dto));
    }

    @PutMapping("/{id}")
    public JobSearchCriteriaDto update(@PathVariable Long id, @RequestBody JobSearchCriteriaDto dto) {
        return mapper.toDto(service.update(id, dto));
    }
}
