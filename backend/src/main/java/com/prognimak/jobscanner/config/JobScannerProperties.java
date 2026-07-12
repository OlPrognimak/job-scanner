package com.prognimak.jobscanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "job-scanner")
public class JobScannerProperties {

    private String candidateProfilePath = "classpath:candidate-profile.md";
    private String candidateContextPath = "file:../documents/candidate-context.md";
    private String cvFilePath = "../documents/cv.docx";
    private boolean aiMatchingDuringScan = false;
    private Scanners scanners = new Scanners();

    public String getCandidateProfilePath() {
        return candidateProfilePath;
    }

    public void setCandidateProfilePath(String candidateProfilePath) {
        this.candidateProfilePath = candidateProfilePath;
    }

    public String getCandidateContextPath() {
        return candidateContextPath;
    }

    public void setCandidateContextPath(String candidateContextPath) {
        this.candidateContextPath = candidateContextPath;
    }

    public String getCvFilePath() {
        return cvFilePath;
    }

    public void setCvFilePath(String cvFilePath) {
        this.cvFilePath = cvFilePath;
    }

    public boolean isAiMatchingDuringScan() {
        return aiMatchingDuringScan;
    }

    public void setAiMatchingDuringScan(boolean aiMatchingDuringScan) {
        this.aiMatchingDuringScan = aiMatchingDuringScan;
    }

    public Scanners getScanners() {
        return scanners;
    }

    public void setScanners(Scanners scanners) {
        this.scanners = scanners;
    }

    public static class Scanners {

        private Freelancermap freelancermap = new Freelancermap();
        private Glassdoor glassdoor = new Glassdoor();
        private Adzuna adzuna = new Adzuna();

        public Freelancermap getFreelancermap() {
            return freelancermap;
        }

        public void setFreelancermap(Freelancermap freelancermap) {
            this.freelancermap = freelancermap;
        }

        public Glassdoor getGlassdoor() {
            return glassdoor;
        }

        public void setGlassdoor(Glassdoor glassdoor) {
            this.glassdoor = glassdoor;
        }

        public Adzuna getAdzuna() {
            return adzuna;
        }

        public void setAdzuna(Adzuna adzuna) {
            this.adzuna = adzuna;
        }
    }

    public static class Freelancermap {

        private String baseUrl = "https://www.freelancermap.de";
        private String searchPath = "/projekte";
        private boolean enabled = true;
        private int maxPages = 1;
        private int maxDetails = 10;
        private int maxCandidates = 20;
        private int maxParallelDetailRequests = 6;
        private long requestDelayMs = 250;
        private int requestTimeoutSeconds = 15;
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

        public int getMaxCandidates() {
            return maxCandidates;
        }

        public void setMaxCandidates(int maxCandidates) {
            this.maxCandidates = maxCandidates;
        }

        public int getMaxParallelDetailRequests() {
            return maxParallelDetailRequests;
        }

        public void setMaxParallelDetailRequests(int maxParallelDetailRequests) {
            this.maxParallelDetailRequests = maxParallelDetailRequests;
        }

        public long getRequestDelayMs() {
            return requestDelayMs;
        }

        public void setRequestDelayMs(long requestDelayMs) {
            this.requestDelayMs = requestDelayMs;
        }

        public int getRequestTimeoutSeconds() {
            return requestTimeoutSeconds;
        }

        public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }
    }

    public static class Glassdoor {

        private String baseUrl = "https://www.glassdoor.de";
        private String searchPath = "/Job/jobs.htm";
        private boolean enabled = true;
        private int maxPages = 1;
        private int maxDetails = 25;
        private int maxCandidates = 40;
        private int maxParallelDetailRequests = 4;
        private long requestDelayMs = 1000;
        private String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

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

        public int getMaxCandidates() {
            return maxCandidates;
        }

        public void setMaxCandidates(int maxCandidates) {
            this.maxCandidates = maxCandidates;
        }

        public int getMaxParallelDetailRequests() {
            return maxParallelDetailRequests;
        }

        public void setMaxParallelDetailRequests(int maxParallelDetailRequests) {
            this.maxParallelDetailRequests = maxParallelDetailRequests;
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

    public static class Adzuna {

        private String baseUrl = "https://api.adzuna.com";
        private String country = "de";
        private String appId = "";
        private String appKey = "";
        private boolean enabled = true;
        private int maxPages = 1;
        private int resultsPerPage = 20;
        private int requestTimeoutSeconds = 15;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppKey() {
            return appKey;
        }

        public void setAppKey(String appKey) {
            this.appKey = appKey;
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

        public int getResultsPerPage() {
            return resultsPerPage;
        }

        public void setResultsPerPage(int resultsPerPage) {
            this.resultsPerPage = resultsPerPage;
        }

        public int getRequestTimeoutSeconds() {
            return requestTimeoutSeconds;
        }

        public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
        }
    }
}
