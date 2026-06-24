package com.prognimak.jobscanner.controller;

import com.prognimak.jobscanner.dto.ApplicationDraftDto;
import com.prognimak.jobscanner.dto.ApplicationDraftUpdateRequest;
import com.prognimak.jobscanner.mapper.ApplicationDraftMapper;
import com.prognimak.jobscanner.service.ApplicationDraftService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drafts")
public class ApplicationDraftController {

    private final ApplicationDraftService applicationDraftService;
    private final ApplicationDraftMapper mapper;

    public ApplicationDraftController(ApplicationDraftService applicationDraftService, ApplicationDraftMapper mapper) {
        this.applicationDraftService = applicationDraftService;
        this.mapper = mapper;
    }

    @PutMapping("/{id}")
    public ApplicationDraftDto update(@PathVariable Long id, @RequestBody ApplicationDraftUpdateRequest request) {
        return mapper.toDto(applicationDraftService.update(id, request));
    }

    @PostMapping("/{id}/send")
    public ApplicationDraftDto send(@PathVariable Long id) {
        return mapper.toDto(applicationDraftService.sendManuallyConfirmed(id));
    }
}
