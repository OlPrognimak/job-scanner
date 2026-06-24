package com.prognimak.jobscanner.ai;

public record AiApplicationSuggestion(
        int matchScore,
        String matchExplanation,
        String anschreibenText
) {
}
