package com.prognimak.jobscanner.controller;

import com.prognimak.jobscanner.dto.ApplicationDraftDto;
import com.prognimak.jobscanner.dto.JobOfferDto;
import com.prognimak.jobscanner.dto.ScanRequest;
import com.prognimak.jobscanner.dto.ScanResultDto;
import com.prognimak.jobscanner.mapper.ApplicationDraftMapper;
import com.prognimak.jobscanner.mapper.JobOfferMapper;
import com.prognimak.jobscanner.service.ApplicationDraftService;
import com.prognimak.jobscanner.service.JobScannerService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobScannerService jobScannerService;
    private final ApplicationDraftService applicationDraftService;
    private final JobOfferMapper jobOfferMapper;
    private final ApplicationDraftMapper draftMapper;

    public JobController(JobScannerService jobScannerService, ApplicationDraftService applicationDraftService,
                         JobOfferMapper jobOfferMapper, ApplicationDraftMapper draftMapper) {
        this.jobScannerService = jobScannerService;
        this.applicationDraftService = applicationDraftService;
        this.jobOfferMapper = jobOfferMapper;
        this.draftMapper = draftMapper;
    }

    @GetMapping
    public List<JobOfferDto> listJobs() {
        return jobScannerService.findAll().stream().map(jobOfferMapper::toDto).toList();
    }

    @GetMapping("/{id}")
    public JobOfferDto getJob(@PathVariable Long id) {
        return jobOfferMapper.toDto(jobScannerService.findById(id));
    }

    @PostMapping("/scan")
    public ScanResultDto scan(@RequestBody(required = false) ScanRequest request) {
        Long criteriaId = request == null ? null : request.criteriaId();
        List<JobOfferDto> jobs = jobScannerService.scan(criteriaId).stream()
                .map(jobOfferMapper::toDto)
                .toList();
        return new ScanResultDto(jobs.size(), jobs);
    }

    @PostMapping("/{id}/generate-anschreiben")
    public ApplicationDraftDto generateAnschreiben(@PathVariable Long id) {
        return draftMapper.toDto(applicationDraftService.generateForJob(id));
    }

    @GetMapping("/{id}/draft")
    public ApplicationDraftDto getDraft(@PathVariable Long id) {
        return draftMapper.toDto(applicationDraftService.findByJobOfferId(id));
    }
}
