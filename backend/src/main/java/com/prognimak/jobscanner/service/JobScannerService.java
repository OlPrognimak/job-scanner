package com.prognimak.jobscanner.service;

import com.prognimak.jobscanner.ai.AiApplicationSuggestion;
import com.prognimak.jobscanner.ai.JobAiService;
import com.prognimak.jobscanner.entity.JobOffer;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import com.prognimak.jobscanner.repository.JobOfferRepository;
import com.prognimak.jobscanner.repository.JobSearchCriteriaRepository;
import com.prognimak.jobscanner.scanner.JobSourceScanner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobScannerService {

    private final List<JobSourceScanner> scanners;
    private final JobOfferRepository jobOfferRepository;
    private final JobSearchCriteriaRepository criteriaRepository;
    private final JobAiService jobAiService;

    public JobScannerService(List<JobSourceScanner> scanners, JobOfferRepository jobOfferRepository,
                             JobSearchCriteriaRepository criteriaRepository, JobAiService jobAiService) {
        this.scanners = scanners;
        this.jobOfferRepository = jobOfferRepository;
        this.criteriaRepository = criteriaRepository;
        this.jobAiService = jobAiService;
    }

    @Transactional
    public List<JobOffer> scan(Long criteriaId) {
        List<JobSearchCriteria> criteriaList = criteriaId == null
                ? criteriaRepository.findByActiveTrue()
                : List.of(criteriaRepository.findById(criteriaId).orElseThrow());

        List<JobOffer> saved = new ArrayList<>();
        for (JobSearchCriteria criteria : criteriaList) {
            for (JobSourceScanner scanner : matchingScanners(criteria)) {
                for (JobOffer scannedOffer : scanner.scan(criteria)) {
                    JobOffer offer = jobOfferRepository.findByJobUrl(scannedOffer.getJobUrl()).orElse(scannedOffer);
                    if (offer.getId() == null) {
                        AiApplicationSuggestion suggestion = jobAiService.createSuggestion(offer);
                        offer.setMatchScore(suggestion.matchScore());
                        offer.setMatchExplanation(suggestion.matchExplanation());
                    }
                    saved.add(jobOfferRepository.save(offer));
                }
            }
        }
        return saved;
    }

    public List<JobOffer> findAll() {
        return jobOfferRepository.findAll().stream()
                .sorted(Comparator.comparing(JobOffer::getDetectedAt).reversed())
                .toList();
    }

    public JobOffer findById(Long id) {
        return jobOfferRepository.findById(id).orElseThrow();
    }

    private List<JobSourceScanner> matchingScanners(JobSearchCriteria criteria) {
        if (criteria.getSourceWebsite() == null || criteria.getSourceWebsite().isBlank()) {
            return scanners;
        }
        String source = criteria.getSourceWebsite().trim();
        return scanners.stream()
                .filter(scanner -> scanner.source().equalsIgnoreCase(source))
                .toList();
    }
}
