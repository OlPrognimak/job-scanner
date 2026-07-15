package com.prognimak.jobscanner.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prognimak.jobscanner.entity.JobOffer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;

class JobAiServiceTest {

    @Test
    void fallbackSuggestionMentionsOnlyRelevantGwtReactInsuranceSignals() {
        CandidateProfileService candidateProfileService = mock(CandidateProfileService.class);
        when(candidateProfileService.loadProfile()).thenReturn("profile");
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatClient.Builder> builderProvider = mock(ObjectProvider.class);
        when(builderProvider.getIfAvailable()).thenReturn(null);

        JobOffer jobOffer = new JobOffer();
        jobOffer.setTitle("Java Full Stack Developer - Fokus Front End");
        jobOffer.setCompany("SThree GmbH");
        jobOffer.setDescription("""
                Projekt in der Versicherungsbranche.
                Migration einer bestehenden GWT-Anwendung nach React und TypeScript.
                Backend mit Java und REST APIs.
                """);

        AiApplicationSuggestion suggestion = new JobAiService(candidateProfileService, builderProvider)
                .createSuggestion(jobOffer);

        assertThat(suggestion.anschreibenText())
                .contains("GWT")
                .contains("React")
                .contains("Versicherung")
                .doesNotContain("oeffentlicher Sektor")
                .doesNotContain("WebSphere")
                .doesNotContain("Struts")
                .doesNotContain("Swing")
                .doesNotContain("AI-gestuetzter Entwicklerwerkzeuge");
    }

    @Test
    void fallbackSuggestionUsesAdjacentLearningForMissingExactSkills() {
        CandidateProfileService candidateProfileService = mock(CandidateProfileService.class);
        when(candidateProfileService.loadProfile()).thenReturn("profile");
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatClient.Builder> builderProvider = mock(ObjectProvider.class);
        when(builderProvider.getIfAvailable()).thenReturn(null);

        JobOffer jobOffer = new JobOffer();
        jobOffer.setTitle("Backend Developer Python");
        jobOffer.setCompany("Example GmbH");
        jobOffer.setDescription("""
                Entwicklung von Backend Services mit Python und FastAPI.
                """);

        AiApplicationSuggestion suggestion = new JobAiService(candidateProfileService, builderProvider)
                .createSuggestion(jobOffer);

        assertThat(suggestion.anschreibenText())
                .contains("AI-gestuetzter Entwicklerwerkzeuge")
                .doesNotContain("praktischer Projektarbeit kenne");
    }

    @Test
    void fallbackSuggestionDoesNotDumpLegacyTechnologiesForGenericBestandssystem() {
        CandidateProfileService candidateProfileService = mock(CandidateProfileService.class);
        when(candidateProfileService.loadProfile()).thenReturn("profile");
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatClient.Builder> builderProvider = mock(ObjectProvider.class);
        when(builderProvider.getIfAvailable()).thenReturn(null);

        JobOffer jobOffer = new JobOffer();
        jobOffer.setTitle("Java Entwickler Bestandssystem");
        jobOffer.setCompany("Example GmbH");
        jobOffer.setDescription("""
                Weiterentwicklung und Modernisierung eines bestehenden Java-Systems.
                """);

        AiApplicationSuggestion suggestion = new JobAiService(candidateProfileService, builderProvider)
                .createSuggestion(jobOffer);

        assertThat(suggestion.anschreibenText())
                .contains("Bestandssystemen")
                .doesNotContain("Swing/SwingX")
                .doesNotContain("JSF/MyFaces")
                .doesNotContain("Struts")
                .doesNotContain("WebSphere")
                .doesNotContain("WebLogic");
    }
}
