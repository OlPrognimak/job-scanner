package com.prognimak.jobscanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "job-scanner")
public class JobScannerProperties {

    private String candidateProfilePath = "classpath:candidate-profile.md";
    private String candidateContextPath = "file:../documents/candidate-context.md";
    private String cvFilePath = "../documents/cv.docx";
    private boolean aiMatchingDuringScan = false;
    private Scanners scanners = new Scanners();
    private Sender sender = new Sender();

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

    public Sender getSender() {
        return sender;
    }

    public void setSender(Sender sender) {
        this.sender = sender;
    }

    public static class Sender {

        private String type = "mock";
        private FreelancermapSender freelancermap = new FreelancermapSender();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public FreelancermapSender getFreelancermap() {
            return freelancermap;
        }

        public void setFreelancermap(FreelancermapSender freelancermap) {
            this.freelancermap = freelancermap;
        }
    }

    public static class FreelancermapSender {

        private String baseUrl = "https://www.freelancermap.de";
        private String loginUrl = "https://www.freelancermap.de/login";
        private String userDataDir = "../.playwright/freelancermap";
        private String browserChannel = "";
        private boolean headless = false;
        private boolean submitEnabled = true;
        private String requiredDocumentLabels = "Lebenslauf_10.07.26.pdf";
        private int slowMoMs = 100;
        private int timeoutSeconds = 180;
        private int manualLoginTimeoutSeconds = 300;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getLoginUrl() {
            return loginUrl;
        }

        public void setLoginUrl(String loginUrl) {
            this.loginUrl = loginUrl;
        }

        public String getUserDataDir() {
            return userDataDir;
        }

        public void setUserDataDir(String userDataDir) {
            this.userDataDir = userDataDir;
        }

        public String getBrowserChannel() {
            return browserChannel;
        }

        public void setBrowserChannel(String browserChannel) {
            this.browserChannel = browserChannel;
        }

        public boolean isHeadless() {
            return headless;
        }

        public void setHeadless(boolean headless) {
            this.headless = headless;
        }

        public boolean isSubmitEnabled() {
            return submitEnabled;
        }

        public void setSubmitEnabled(boolean submitEnabled) {
            this.submitEnabled = submitEnabled;
        }

        public String getRequiredDocumentLabels() {
            return requiredDocumentLabels;
        }

        public void setRequiredDocumentLabels(String requiredDocumentLabels) {
            this.requiredDocumentLabels = requiredDocumentLabels;
        }

        public int getSlowMoMs() {
            return slowMoMs;
        }

        public void setSlowMoMs(int slowMoMs) {
            this.slowMoMs = slowMoMs;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getManualLoginTimeoutSeconds() {
            return manualLoginTimeoutSeconds;
        }

        public void setManualLoginTimeoutSeconds(int manualLoginTimeoutSeconds) {
            this.manualLoginTimeoutSeconds = manualLoginTimeoutSeconds;
        }
    }

    public static class Scanners {

        private Freelancermap freelancermap = new Freelancermap();
        private Glassdoor glassdoor = new Glassdoor();
        private Adzuna adzuna = new Adzuna();
        private Arbeitsagentur arbeitsagentur = new Arbeitsagentur();
        private Meinestadt meinestadt = new Meinestadt();

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

        public Arbeitsagentur getArbeitsagentur() {
            return arbeitsagentur;
        }

        public void setArbeitsagentur(Arbeitsagentur arbeitsagentur) {
            this.arbeitsagentur = arbeitsagentur;
        }

        public Meinestadt getMeinestadt() {
            return meinestadt;
        }

        public void setMeinestadt(Meinestadt meinestadt) {
            this.meinestadt = meinestadt;
        }
    }

    public static class Freelancermap {

        private String baseUrl = "https://www.freelancermap.de";
        private String searchPath = "/projekte";
        private boolean enabled = true;
        private int maxPages = 3;
        private int maxDetails = 10;
        private int maxCandidates = 100;
        private int maxParallelDetailRequests = 6;
        private long requestDelayMs = 250;
        private int requestTimeoutSeconds = 15;
        private String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36";
        private String cookieHeader = "";
        private String cookieFile = "";
        private String cookieNames = "REMEMBERME,PHPSESSID";

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

        public String getCookieHeader() {
            return cookieHeader;
        }

        public void setCookieHeader(String cookieHeader) {
            this.cookieHeader = cookieHeader;
        }

        public String getCookieFile() {
            return cookieFile;
        }

        public void setCookieFile(String cookieFile) {
            this.cookieFile = cookieFile;
        }

        public String getCookieNames() {
            return cookieNames;
        }

        public void setCookieNames(String cookieNames) {
            this.cookieNames = cookieNames;
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

    public static class Arbeitsagentur {

        private String baseUrl = "https://rest.arbeitsagentur.de";
        private String websiteBaseUrl = "https://www.arbeitsagentur.de";
        private String apiKey = "jobboerse-jobsuche";
        private boolean enabled = true;
        private int maxPages = 1;
        private int resultsPerPage = 25;
        private int maxDetails = 25;
        private int maxParallelDetailRequests = 6;
        private int requestTimeoutSeconds = 20;
        private int publishedWithinDays = 100;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getWebsiteBaseUrl() {
            return websiteBaseUrl;
        }

        public void setWebsiteBaseUrl(String websiteBaseUrl) {
            this.websiteBaseUrl = websiteBaseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
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

        public int getMaxDetails() {
            return maxDetails;
        }

        public void setMaxDetails(int maxDetails) {
            this.maxDetails = maxDetails;
        }

        public int getMaxParallelDetailRequests() {
            return maxParallelDetailRequests;
        }

        public void setMaxParallelDetailRequests(int maxParallelDetailRequests) {
            this.maxParallelDetailRequests = maxParallelDetailRequests;
        }

        public int getRequestTimeoutSeconds() {
            return requestTimeoutSeconds;
        }

        public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
        }

        public int getPublishedWithinDays() {
            return publishedWithinDays;
        }

        public void setPublishedWithinDays(int publishedWithinDays) {
            this.publishedWithinDays = publishedWithinDays;
        }
    }

    public static class Meinestadt {

        private String baseUrl = "https://jobs.meinestadt.de";
        private String searchPath = "/deutschland";
        private boolean enabled = true;
        private int maxPages = 1;
        private int maxDetails = 20;
        private int maxCandidates = 30;
        private int maxParallelDetailRequests = 4;
        private long requestDelayMs = 500;
        private int requestTimeoutSeconds = 20;
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
}
