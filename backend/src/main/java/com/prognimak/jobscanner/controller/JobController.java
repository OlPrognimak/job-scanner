package com.prognimak.jobscanner.controller;

import com.prognimak.jobscanner.dto.ApplicationDraftDto;
import com.prognimak.jobscanner.dto.JobOfferDto;
import com.prognimak.jobscanner.dto.PageDto;
import com.prognimak.jobscanner.dto.ScanRequest;
import com.prognimak.jobscanner.dto.ScanResultDto;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import com.prognimak.jobscanner.mapper.ApplicationDraftMapper;
import com.prognimak.jobscanner.mapper.JobOfferMapper;
import com.prognimak.jobscanner.service.ApplicationDraftService;
import com.prognimak.jobscanner.service.JobScanResult;
import com.prognimak.jobscanner.service.JobScannerService;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/history")
    public PageDto<JobOfferDto> history(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "25") int size) {
        Page<JobOfferDto> historyPage = jobScannerService.findHistory(page, size)
                .map(jobOfferMapper::toDto);
        return new PageDto<>(
                historyPage.getContent(),
                historyPage.getNumber(),
                historyPage.getSize(),
                historyPage.getTotalElements(),
                historyPage.getTotalPages(),
                historyPage.isFirst(),
                historyPage.isLast()
        );
    }

    @GetMapping("/{id}")
    public JobOfferDto getJob(@PathVariable Long id) {
        return jobOfferMapper.toDto(jobScannerService.findById(id));
    }

    @PostMapping("/scan")
    public ScanResultDto scan(@RequestBody(required = false) ScanRequest request) {
        Long criteriaId = request == null ? null : request.criteriaId();
        JobScanResult result = criteriaId == null && hasAdHocCriteria(request)
                ? jobScannerService.scan(toCriteria(request))
                : jobScannerService.scan(criteriaId);
        List<JobOfferDto> jobs = result.jobs().stream()
                .map(jobOfferMapper::toDto)
                .toList();
        return new ScanResultDto(jobs.size(), jobs, result.messages());
    }

    private boolean hasAdHocCriteria(ScanRequest request) {
        return request != null
                && (hasText(request.keyword())
                || hasText(request.country())
                || hasText(request.location())
                || hasText(request.sourceWebsite()));
    }

    private JobSearchCriteria toCriteria(ScanRequest request) {
        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setName("Ad-hoc scan");
        criteria.setKeyword(request.keyword());
        criteria.setCountry(request.country());
        criteria.setLocation(normalizeLocation(request.location()));
        criteria.setSourceWebsite(request.sourceWebsite());
        criteria.setActive(true);
        return criteria;
    }

    private String normalizeLocation(String location) {
        if (!hasText(location)) {
            return null;
        }
        String normalized = location.trim().toLowerCase();
        if (normalized.equals("de")
                || normalized.equals("deutschland")
                || normalized.equals("germany")
                || normalized.equals("germania")) {
            return null;
        }
        return location.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
