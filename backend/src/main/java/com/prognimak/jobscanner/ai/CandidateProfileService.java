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
        String baseProfile = loadRequiredResource(properties.getCandidateProfilePath(), "Candidate profile");
        String cvContext = loadOptionalResource(properties.getCandidateContextPath(),
                "file:../documents/candidate-context.md", "file:documents/candidate-context.md");
        if (cvContext.isBlank()) {
            return baseProfile;
        }
        return baseProfile + "\n\n" + cvContext;
    }

    private String loadRequiredResource(String path, String label) {
        Resource resource = resourceLoader.getResource(path);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(label + " could not be loaded from " + path, ex);
        }
    }

    private String loadOptionalResource(String path, String... fallbackPaths) {
        String[] paths = new String[fallbackPaths.length + 1];
        paths[0] = path;
        System.arraycopy(fallbackPaths, 0, paths, 1, fallbackPaths.length);

        for (String candidatePath : paths) {
            if (candidatePath == null || candidatePath.isBlank()) {
                continue;
            }
            Resource resource = resourceLoader.getResource(candidatePath);
            if (!resource.exists()) {
                continue;
            }
            try {
                return resource.getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalStateException("Candidate context could not be loaded from " + candidatePath, ex);
            }
        }
        return "";
    }
}
