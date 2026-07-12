package com.prognimak.jobscanner.scanner;

public class ScannerBlockedException extends RuntimeException {

    private final String source;

    public ScannerBlockedException(String source, String message, Throwable cause) {
        super(message, cause);
        this.source = source;
    }

    public String getSource() {
        return source;
    }
}
