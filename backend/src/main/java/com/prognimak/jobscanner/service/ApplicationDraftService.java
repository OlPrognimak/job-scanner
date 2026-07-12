package com.prognimak.jobscanner.service;

import com.prognimak.jobscanner.ai.AiApplicationSuggestion;
import com.prognimak.jobscanner.ai.JobAiService;
import com.prognimak.jobscanner.config.JobScannerProperties;
import com.prognimak.jobscanner.dto.ApplicationDraftUpdateRequest;
import com.prognimak.jobscanner.entity.ApplicationDraft;
import com.prognimak.jobscanner.entity.DraftStatus;
import com.prognimak.jobscanner.entity.JobOffer;
import com.prognimak.jobscanner.entity.JobStatus;
import com.prognimak.jobscanner.repository.ApplicationDraftRepository;
import com.prognimak.jobscanner.repository.JobOfferRepository;
import com.prognimak.jobscanner.sender.ApplicationSender;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationDraftService {

    private final ApplicationDraftRepository draftRepository;
    private final JobOfferRepository jobOfferRepository;
    private final JobAiService jobAiService;
    private final ApplicationSender applicationSender;
    private final JobScannerProperties properties;

    public ApplicationDraftService(ApplicationDraftRepository draftRepository, JobOfferRepository jobOfferRepository,
                                   JobAiService jobAiService, ApplicationSender applicationSender,
                                   JobScannerProperties properties) {
        this.draftRepository = draftRepository;
        this.jobOfferRepository = jobOfferRepository;
        this.jobAiService = jobAiService;
        this.applicationSender = applicationSender;
        this.properties = properties;
    }

    @Transactional
    public ApplicationDraft generateForJob(Long jobOfferId) {
        JobOffer jobOffer = jobOfferRepository.findById(jobOfferId).orElseThrow();
        AiApplicationSuggestion suggestion = jobAiService.createSuggestion(jobOffer);
        jobOffer.setMatchScore(suggestion.matchScore());
        jobOffer.setMatchExplanation(suggestion.matchExplanation());
        jobOffer.setStatus(JobStatus.REVIEWED);

        ApplicationDraft draft = draftRepository.findByJobOfferId(jobOfferId).orElseGet(ApplicationDraft::new);
        draft.setJobOffer(jobOffer);
        draft.setAnschreibenText(suggestion.anschreibenText());
        if (draft.getCvFilePath() == null || draft.getCvFilePath().isBlank()) {
            draft.setCvFilePath(properties.getCvFilePath());
        }
        draft.setStatus(DraftStatus.DRAFT);
        jobOfferRepository.save(jobOffer);
        return draftRepository.save(draft);
    }

    public ApplicationDraft findByJobOfferId(Long jobOfferId) {
        return draftRepository.findByJobOfferId(jobOfferId).orElseThrow();
    }

    @Transactional
    public ApplicationDraft update(Long id, ApplicationDraftUpdateRequest request) {
        ApplicationDraft draft = draftRepository.findById(id).orElseThrow();
        draft.setAnschreibenText(request.anschreibenText());
        draft.setCvFilePath(request.cvFilePath());
        draft.setCvDocumentId(request.cvDocumentId());
        return draftRepository.save(draft);
    }

    @Transactional
    public ApplicationDraft sendManuallyConfirmed(Long id) {
        ApplicationDraft draft = draftRepository.findById(id).orElseThrow();
        if (draft.getCvFilePath() == null || draft.getCvFilePath().isBlank()) {
            draft.setCvFilePath(properties.getCvFilePath());
        }
        applicationSender.send(draft);
        draft.setStatus(DraftStatus.SENT);
        draft.setSentAt(Instant.now());
        draft.getJobOffer().setStatus(JobStatus.APPLIED);
        jobOfferRepository.save(draft.getJobOffer());
        return draftRepository.save(draft);
    }
}
