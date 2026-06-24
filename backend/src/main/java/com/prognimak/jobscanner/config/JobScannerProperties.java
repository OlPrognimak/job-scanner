package com.prognimak.jobscanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "job-scanner")
public class JobScannerProperties {

    private String candidateProfilePath = "classpath:candidate-profile.md";

    public String getCandidateProfilePath() {
        return candidateProfilePath;
    }

    public void setCandidateProfilePath(String candidateProfilePath) {
        this.candidateProfilePath = candidateProfilePath;
    }
}
