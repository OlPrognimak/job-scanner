package com.prognimak.jobscanner.ai;

import com.prognimak.jobscanner.entity.JobOffer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class JobAiService {

    private final CandidateProfileService candidateProfileService;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;

    public JobAiService(CandidateProfileService candidateProfileService,
                        ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        this.candidateProfileService = candidateProfileService;
        this.chatClientBuilderProvider = chatClientBuilderProvider;
    }

    public AiApplicationSuggestion createSuggestion(JobOffer jobOffer) {
        String profile = candidateProfileService.loadProfile();
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            return fallbackSuggestion(jobOffer);
        }

        try {
            String content = builder.build()
                    .prompt()
                    .system("""
                            Du bist ein Bewerbungsassistent fuer deutsche Bewerbungen.
                            Du bewertest Job-Fit und erstellst nur Entwuerfe.
                            Du darfst niemals behaupten, dass eine Bewerbung versendet wurde.
                            Antworte exakt in drei Abschnitten:
                            MATCH_SCORE: <Zahl 0-100>
                            MATCH_EXPLANATION: <kurze deutsche Begruendung>
                            ANSCHREIBEN: <professionelles, knappes deutsches Anschreiben>
                            """)
                    .user("""
                            Kandidatenprofil:
                            %s

                            Job:
                            Titel: %s
                            Firma: %s
                            Ort: %s
                            Beschreibung:
                            %s
                            """.formatted(profile, jobOffer.getTitle(), jobOffer.getCompany(),
                            jobOffer.getLocation(), jobOffer.getDescription()))
                    .call()
                    .content();
            return parseResponse(content, jobOffer);
        } catch (RuntimeException ex) {
            return fallbackSuggestion(jobOffer);
        }
    }

    private AiApplicationSuggestion parseResponse(String content, JobOffer jobOffer) {
        int score = parseScore(content);
        String explanation = section(content, "MATCH_EXPLANATION:", "ANSCHREIBEN:");
        String anschreiben = section(content, "ANSCHREIBEN:", null);
        if (anschreiben.isBlank()) {
            return fallbackSuggestion(jobOffer);
        }
        return new AiApplicationSuggestion(score, explanation, anschreiben);
    }

    private int parseScore(String content) {
        String scoreSection = section(content, "MATCH_SCORE:", "MATCH_EXPLANATION:");
        try {
            int score = Integer.parseInt(scoreSection.replaceAll("[^0-9]", ""));
            return Math.max(0, Math.min(100, score));
        } catch (NumberFormatException ex) {
            return 75;
        }
    }

    private String section(String content, String start, String end) {
        int startIndex = content.indexOf(start);
        if (startIndex < 0) {
            return "";
        }
        int valueStart = startIndex + start.length();
        int valueEnd = end == null ? content.length() : content.indexOf(end, valueStart);
        if (valueEnd < 0) {
            valueEnd = content.length();
        }
        return content.substring(valueStart, valueEnd).trim();
    }

    private AiApplicationSuggestion fallbackSuggestion(JobOffer jobOffer) {
        String company = jobOffer.getCompany() == null || jobOffer.getCompany().isBlank()
                ? "Ihr Unternehmen"
                : jobOffer.getCompany();
        String text = """
                Sehr geehrte Damen und Herren,

                mit grossem Interesse habe ich Ihre Ausschreibung "%s" gelesen. Die beschriebenen Aufgaben passen sehr gut zu meiner Erfahrung in der Entwicklung stabiler Java- und Spring-Boot-Loesungen, REST APIs und datenbankgestuetzter Backend-Systeme.

                Besonders reizt mich die Moeglichkeit, meine praktische Projekterfahrung strukturiert in Ihr Team einzubringen und schnell produktive Ergebnisse zu liefern. Gerne erlaeutere ich Ihnen in einem Gespraech, wie ich %s konkret unterstuetzen kann.

                Mit freundlichen Gruessen
                """.formatted(jobOffer.getTitle(), company);
        return new AiApplicationSuggestion(
                75,
                "Vorlaeufige Bewertung ohne aktiven AI-Provider. Die Rolle passt grundsaetzlich zu Java, Spring Boot und Backend-Erfahrung.",
                text
        );
    }
}
