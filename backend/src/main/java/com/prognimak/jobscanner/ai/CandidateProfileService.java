package com.prognimak.jobscanner.ai;

import com.prognimak.jobscanner.config.JobScannerProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
public class CandidateProfileService {

    private final ResourceLoader resourceLoader;
    private final JobScannerProperties properties;

    public CandidateProfileService(ResourceLoader resourceLoader, JobScannerProperties properties) {
        this.resourceLoader = resourceLoader;
        this.properties = properties;
    }

    public String loadProfile() {
        Resource resource = resourceLoader.getResource(properties.getCandidateProfilePath());
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Candidate profile could not be loaded from "
                    + properties.getCandidateProfilePath(), ex);
        }
    }
}
