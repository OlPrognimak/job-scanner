package com.prognimak.jobscanner.service;

import com.prognimak.jobscanner.ai.AiApplicationSuggestion;
import com.prognimak.jobscanner.ai.JobAiService;
import com.prognimak.jobscanner.config.JobScannerProperties;
import com.prognimak.jobscanner.entity.JobOffer;
import com.prognimak.jobscanner.entity.JobSearchCriteria;
import com.prognimak.jobscanner.repository.JobOfferRepository;
import com.prognimak.jobscanner.repository.JobSearchCriteriaRepository;
import com.prognimak.jobscanner.scanner.JobSourceScanner;
import com.prognimak.jobscanner.scanner.ScannerBlockedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobScannerService {

    private final List<JobSourceScanner> scanners;
    private final JobOfferRepository jobOfferRepository;
    private final JobSearchCriteriaRepository criteriaRepository;
    private final JobAiService jobAiService;
    private final JobScannerProperties properties;

    public JobScannerService(List<JobSourceScanner> scanners, JobOfferRepository jobOfferRepository,
                             JobSearchCriteriaRepository criteriaRepository, JobAiService jobAiService,
                             JobScannerProperties properties) {
        this.scanners = scanners;
        this.jobOfferRepository = jobOfferRepository;
        this.criteriaRepository = criteriaRepository;
        this.jobAiService = jobAiService;
        this.properties = properties;
    }

    @Transactional
    public JobScanResult scan(Long criteriaId) {
        List<JobSearchCriteria> criteriaList = criteriaId == null
                ? criteriaRepository.findByActiveTrue()
                : List.of(criteriaRepository.findById(criteriaId).orElseThrow());

        return scan(criteriaList);
    }

    @Transactional
    public JobScanResult scan(JobSearchCriteria criteria) {
        return scan(List.of(criteria));
    }

    private JobScanResult scan(List<JobSearchCriteria> criteriaList) {
        List<JobOffer> saved = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        for (JobSearchCriteria criteria : criteriaList) {
            for (JobSourceScanner scanner : matchingScanners(criteria)) {
                long scannerStartedAt = System.nanoTime();
                List<JobOffer> scannedOffers;
                try {
                    scannedOffers = scanner.scan(criteria);
                } catch (ScannerBlockedException ex) {
                    messages.add(ex.getMessage());
                    continue;
                } catch (RuntimeException ex) {
                    messages.add("%s scan failed: %s".formatted(scanner.source(), ex.getMessage()));
                    continue;
                }
                int sourceSavedCount = 0;
                for (JobOffer scannedOffer : scannedOffers) {
                    JobOffer offer = jobOfferRepository.findByJobUrl(scannedOffer.getJobUrl())
                            .map(existingOffer -> refreshFromScan(existingOffer, scannedOffer))
                            .orElse(scannedOffer);
                    if (offer.getId() == null && properties.isAiMatchingDuringScan()) {
                        AiApplicationSuggestion suggestion = jobAiService.createSuggestion(offer);
                        offer.setMatchScore(suggestion.matchScore());
                        offer.setMatchExplanation(suggestion.matchExplanation());
                    }
                    saved.add(jobOfferRepository.save(offer));
                    sourceSavedCount++;
                }
                long elapsedMs = (System.nanoTime() - scannerStartedAt) / 1_000_000;
                messages.add("%s scan finished: %d jobs imported in %d ms%s.".formatted(
                        scanner.source(),
                        sourceSavedCount,
                        elapsedMs,
                        properties.isAiMatchingDuringScan() ? "" : " (AI matching during scan is disabled)"
                ));
            }
        }
        return new JobScanResult(saved, messages);
    }

    public List<JobOffer> findAll() {
        return jobOfferRepository.findAll().stream()
                .filter(this::isVisibleJobOffer)
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
        Set<String> sources = Arrays.stream(criteria.getSourceWebsite().split("[,;]"))
                .map(String::trim)
                .filter(source -> !source.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        if (sources.isEmpty()) {
            return scanners;
        }
        return scanners.stream()
                .filter(scanner -> matchesAnySource(scanner, sources))
                .toList();
    }

    private boolean matchesAnySource(JobSourceScanner scanner, Set<String> sources) {
        String scannerSource = scanner.source().toLowerCase();
        return sources.stream()
                .anyMatch(source -> source.equals(scannerSource)
                        || source.contains(scannerSource)
                        || scannerSource.contains(source));
    }

    private JobOffer refreshFromScan(JobOffer existingOffer, JobOffer scannedOffer) {
        existingOffer.setSource(scannedOffer.getSource());
        existingOffer.setTitle(scannedOffer.getTitle());
        existingOffer.setCompany(scannedOffer.getCompany());
        existingOffer.setLocation(scannedOffer.getLocation());
        existingOffer.setRemoteType(scannedOffer.getRemoteType());
        existingOffer.setContractType(scannedOffer.getContractType());
        existingOffer.setDescription(scannedOffer.getDescription());
        existingOffer.setDetectedAt(scannedOffer.getDetectedAt());
        existingOffer.setRateOrSalary(scannedOffer.getRateOrSalary());
        return existingOffer;
    }

    private boolean isVisibleJobOffer(JobOffer jobOffer) {
        if (!"freelancermap".equalsIgnoreCase(jobOffer.getSource()) || jobOffer.getTitle() == null) {
            return true;
        }
        String title = jobOffer.getTitle().toLowerCase();
        return !title.contains("finden sie das passende projekt")
                && !title.contains("freelance projekte finden")
                && !title.contains("projektboerse")
                && !title.contains("projektbörse");
    }
}
