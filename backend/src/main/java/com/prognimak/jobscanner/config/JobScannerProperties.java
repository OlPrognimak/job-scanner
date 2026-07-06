package com.prognimak.jobscanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "job-scanner")
public class JobScannerProperties {

    private String candidateProfilePath = "classpath:candidate-profile.md";
    private Scanners scanners = new Scanners();

    public String getCandidateProfilePath() {
        return candidateProfilePath;
    }

    public void setCandidateProfilePath(String candidateProfilePath) {
        this.candidateProfilePath = candidateProfilePath;
    }

    public Scanners getScanners() {
        return scanners;
    }

    public void setScanners(Scanners scanners) {
        this.scanners = scanners;
    }

    public static class Scanners {

        private Freelancermap freelancermap = new Freelancermap();

        public Freelancermap getFreelancermap() {
            return freelancermap;
        }

        public void setFreelancermap(Freelancermap freelancermap) {
            this.freelancermap = freelancermap;
        }
    }

    public static class Freelancermap {

        private String baseUrl = "https://www.freelancermap.de";
        private String searchPath = "/projektboerse.html";
        private boolean enabled = true;
        private int maxPages = 1;
        private int maxDetails = 10;
        private long requestDelayMs = 1500;
        private String userAgent = "Mozilla/5.0 JobScannerBot/1.0";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getSearchPath() {
            return searchPath;
        }

        public void setSearchPath(String searchPath) {
            this.searchPath = searchPath;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxPages() {
            return maxPages;
        }

        public void setMaxPages(int maxPages) {
            this.maxPages = maxPages;
        }

        public int getMaxDetails() {
            return maxDetails;
        }

        public void setMaxDetails(int maxDetails) {
            this.maxDetails = maxDetails;
        }

        public long getRequestDelayMs() {
            return requestDelayMs;
        }

        public void setRequestDelayMs(long requestDelayMs) {
            this.requestDelayMs = requestDelayMs;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }
    }
}
