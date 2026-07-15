package com.prognimak.jobscanner.ai;

import com.prognimak.jobscanner.entity.JobOffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class JobAiService {

    private static final Logger log = LoggerFactory.getLogger(JobAiService.class);

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
                            Stelle Technologien, die im Kandidatenprofil als Erfahrung genannt sind, niemals als fehlende praktische Erfahrung dar.
                            Wenn einzelne Job-Technologien nicht im Kandidatenprofil stehen, nenne sie hoechstens neutral als angrenzende oder vertiefbare Themen.
                            Strikte Relevanzregel: Erwaehne im Anschreiben nur Skills, Domaenen und Referenzprojekte, die durch Titel oder Beschreibung der Stelle
                            direkt ausgeloest werden. Nutze maximal 3 bis 5 relevante Signale. Liste niemals viele CV-Technologien auf.
                            Erwaehne keine Domaene wie Public Sector, Healthcare, Banking, Automotive, Security oder Reporting, wenn sie in der Stelle nicht vorkommt.
                            Erwaehne keine Legacy-Technologien wie Swing, JSF, JSP, Struts, EJB, WebSphere oder WebLogic, wenn die Stelle nur GWT zu React beschreibt.
                            Wenn eine geforderte Technologie nicht im Kandidatenprofil steht, suche zuerst nach sachlich naher Erfahrung
                            z.B. aehnliches Framework, gleiche Plattform, gleiche Domaene, gleiche Architektur- oder Migrationsaufgabe.
                            Formuliere dann ehrlich: "direkte Projekterfahrung mit X ist nicht der Schwerpunkt meines Profils, aber Y/Z sind nah verwandt".
                            Wenn keine nahe Erfahrung erkennbar ist, erwaehne kurz schnelle Einarbeitung mit AI-gestuetzten Entwicklerwerkzeugen,
                            ohne zu behaupten, dass die Technologie bereits praktisch beherrscht wird.
                            Analysiere nicht nur Muss-Anforderungen, sondern auch Nice-to-have-Anforderungen, Migrationskontext und Legacy-to-Modernisierungswert.
                            Wenn das Projekt eine Migration beschreibt, z.B. GWT zu React, Vaadin zu moderner UI, JEE zu Spring Boot oder Angular-Migration,
                            dann bewerte passende Legacy- und Zieltechnologien aus dem Kandidatenprofil als starkes Plus.
                            Wenn die Stelle Legacy-Code, Altanwendungen, Bestandssysteme, Refactoring, Wartung oder klassische Java-Technologien nennt
                            wie Swing, JSF, JSP, JSTL, Struts, Servlets, EJB, SOAP, WebSphere, WebLogic, JBoss/WildFly oder Tomcat,
                            dann stelle diese Erfahrung als positiven Modernisierungs- und Risikoabbau-Faktor dar.
                            Analysiere auch die Domaene der Stelle: Healthcare, Versicherung, Public Sector, Automotive, Banking, Finance/Trading, Security/Kryptographie,
                            Reporting/Dokumente, Compliance oder andere Fachkontexte. Wenn das Kandidatenprofil konkrete Projektbeispiele fuer diese Domaene nennt,
                            nenne ein bis zwei passende Referenzprojekte im Anschreiben.
                            Nenne konkrete Ueberschneidungen aus Jobbeschreibung und Kandidatenprofil im Anschreiben, aber erfinde keine Projekterfahrung.
                            Antworte exakt in drei Abschnitten:
                            MATCH_SCORE: <Zahl 0-100>
                            MATCH_EXPLANATION: <kurze deutsche Begruendung mit Muss-Fit, Nice-to-have-Fit, Domaenenfit und Migrationsmehrwert>
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
            log.warn("AI suggestion generation failed. Falling back to local draft for job '{}': {}",
                    jobOffer.getTitle(), ex.getMessage());
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
        List<String> relevantSignals = relevantSignals(jobOffer);
        String signalSentence = relevantSignals.isEmpty()
                ? "Die beschriebenen Aufgaben passen sehr gut zu meiner langjaehrigen Erfahrung in Java, Spring Boot, REST APIs, modernen Frontends und datenbankgestuetzten Enterprise-Systemen."
                : "Besonders relevant sind fuer mich " + joinGerman(relevantSignals)
                + ", weil ich diese Themen aus langjaehriger praktischer Projektarbeit kenne.";
        String migrationSentence = migrationSentence(jobOffer);
        String domainSentence = domainSentence(jobOffer);
        String adjacentLearningSentence = adjacentLearningSentence(jobOffer, relevantSignals);
        String text = """
                Sehr geehrte Damen und Herren,

                mit grossem Interesse habe ich Ihre Ausschreibung "%s" gelesen. %s

                %s%s%sMit mehr als 33 Jahren IT-Erfahrung und mehr als 27 Jahren Java-Erfahrung kann ich fachliche Anforderungen, bestehende Systemlogik und moderne Umsetzung pragmatisch verbinden. Gerne erlaeutere ich Ihnen in einem Gespraech, wie ich %s konkret unterstuetzen kann.

                Mit freundlichen Gruessen
                """.formatted(jobOffer.getTitle(), signalSentence, domainSentence, migrationSentence,
                adjacentLearningSentence, company);
        return new AiApplicationSuggestion(
                fallbackScore(relevantSignals, migrationSentence, domainSentence),
                fallbackExplanation(relevantSignals, migrationSentence, domainSentence),
                text
        );
    }

    private List<String> relevantSignals(JobOffer jobOffer) {
        String text = jobText(jobOffer);
        List<String> signals = new ArrayList<>();
        addIfPresent(signals, text, "Java", "java ");
        addIfPresent(signals, text, "Spring Boot", "spring boot", "springboot");
        addIfPresent(signals, text, "React", "react");
        addIfPresent(signals, text, "GWT/RestyGWT", "gwt", "restygwt");
        addIfPresent(signals, text, "Vaadin", "vaadin");
        addIfPresent(signals, text, "Oracle/SQL", "oracle", "sql", "pl/sql", "plsql");
        addIfPresent(signals, text, "REST APIs", "rest ", "rest-api", "restful");
        addIfPresent(signals, text, "TypeScript/JavaScript", "typescript", "javascript");
        addIfPresent(signals, text, "Security/Identity", "security", "oauth", "openid", "keycloak", "okta", "ldap", "x.509", "x509", "pki");
        addIfPresent(signals, text, "Reporting", "reporting", "jasper", "jaspersoft", "oracle report", "pdf", "csv");
        addIfPresent(signals, text, "Legacy-Code-Modernisierung", "legacy", "altanwendung", "bestandssystem");
        addIfPresent(signals, text, "klassische Java-Webtechnologien", "jsf", "jsp", "jstl", "taglib",
                "struts", "servlet", "ejb", "soap", "websphere", "weblogic");
        addIfPresent(signals, text, "Swing/Rich-Client", "swing", "swingx", "rich-client", "rich client",
                "standalone", "desktop");
        return signals.stream().limit(5).toList();
    }

    private void addIfPresent(List<String> signals, String text, String signal, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                signals.add(signal);
                return;
            }
        }
    }

    private String migrationSentence(JobOffer jobOffer) {
        String text = jobText(jobOffer);
        if (text.contains("gwt") && text.contains("react")) {
            return "Der Migrationskontext von GWT zu React ist dabei ein klarer Pluspunkt: Ich bringe praktische GWT-/RestyGWT-Erfahrung ebenso mit wie moderne Frontend-Erfahrung mit React, Angular, Vue.js und TypeScript. ";
        }
        if (containsAny(text, "legacy", "altanwendung", "bestandssystem", "jsf", "jsp", "jstl", "struts",
                "swing", "ejb", "websphere", "weblogic")) {
            List<String> legacyTechnologies = legacyTechnologies(text);
            if (legacyTechnologies.isEmpty()) {
                return "Bei Bestandssystemen und Legacy-Code bringe ich Erfahrung in Analyse, Refactoring und schrittweiser Modernisierung langlebiger Enterprise-Anwendungen mit. ";
            }
            return "Bei den genannten Bestandstechnologien " + joinGerman(legacyTechnologies)
                    + " bringe ich praktische Erfahrung in Analyse, Refactoring und Modernisierung mit. ";
        }
        if (text.contains("migration") || text.contains("migrat") || text.contains("modernisierung")) {
            return "Der Modernisierungs- und Migrationskontext passt besonders gut, da ich langlebige Enterprise-Systeme bereits mehrfach technologisch weiterentwickelt und migriert habe. ";
        }
        return "";
    }

    private String domainSentence(JobOffer jobOffer) {
        String text = jobText(jobOffer);
        List<String> domains = new ArrayList<>();
        addIfPresent(domains, text,
                "Versicherung/Healthcare mit Medicproof, Krankenversicherung sowie eGK/Gematik-Kontext",
                "versicherung", "krankenversicherung", "healthcare", "gesundheit", "medizin", "gematik", "egk");
        addIfPresent(domains, text,
                "oeffentlicher Sektor mit SLA Niedersachsen, T-Systems Meldungsportal und BMI/ITZBund Portal-Verbund",
                "public sector", "oeffentlich", "öffentlich", "verwaltung", "behoerde", "behörde", "bund", "land");
        addIfPresent(domains, text,
                "Security/Kryptographie mit PKI, X.509, RSA/ECC, BouncyCastle, Spring Security, OKTA und OAuth2/OpenID",
                "security", "krypt", "crypt", "pki", "x.509", "x509", "rsa", "ecc", "certificate", "zertifikat",
                "oauth", "openid", "okta", "keycloak", "ldap");
        addIfPresent(domains, text,
                "Reporting mit JasperReports/TIBCO Jaspersoft, Oracle Reports, BIRT und komplexen SQL-basierten Reports",
                "reporting", "report", "jasper", "jaspersoft", "oracle reports", "oracle report", "birt", "pdf", "csv");
        addIfPresent(domains, text,
                "Banking/Compliance mit Postbank, GWG, Oracle Forms/Reports, Oracle DB und PL/SQL",
                "bank", "banking", "compliance", "gwg", "regulatorik", "regulatory");
        addIfPresent(domains, text,
                "Finance/Trading mit Aktien, Kryptowaehrungen, Futures, Portfolios, Watchlists und Echtzeitdaten",
                "finance", "trading", "aktien", "krypto", "crypto", "futures", "portfolio", "marktdaten", "market data");
        addIfPresent(domains, text,
                "Automotive-nahe Integrations- und Enterprise-Erfahrung; branchenspezifische Prozesse kann ich durch meine langjaehrige Fachsystem-Erfahrung schnell aufnehmen",
                "automotive", "fahrzeug", "vehicle", "mobility", "mobilitaet", "mobilität");
        if (domains.isEmpty()) {
            return "";
        }
        return "Auch der fachliche Kontext passt: " + joinGerman(domains) + ". ";
    }

    private String adjacentLearningSentence(JobOffer jobOffer, List<String> relevantSignals) {
        if (relevantSignals.size() >= 3) {
            return "";
        }
        String text = jobText(jobOffer);
        if (containsAny(text, "python", "go ", "golang", "ruby", "php", "scala", "rust", "node.js", "nodejs")) {
            return "Falls einzelne geforderte Technologien nicht exakt meinem bisherigen Schwerpunkt entsprechen, kann ich sie durch meine breite Java-/Full-Stack-Basis und den routinierten Einsatz AI-gestuetzter Entwicklerwerkzeuge schnell projektbezogen aufnehmen. ";
        }
        if (containsAny(text, "aws", "gcp", "terraform", "kubernetes", "helm")) {
            return "Bei einzelnen Infrastrukturthemen, die nicht exakt im Mittelpunkt meines Profils stehen, kann ich auf Docker-, Azure-/AKS- und CI/CD-nahe Erfahrung aufbauen und mich mit AI-gestuetzten Entwicklerwerkzeugen schnell in projektspezifische Details einarbeiten. ";
        }
        if (containsAny(text, "svelte", "ember", "backbone", "jquery", "solidjs", "qwik")) {
            return "Bei einzelnen Frontend-Frameworks ausserhalb meines Kernprofils kann ich auf langjaehrige Erfahrung mit React, Angular, Vue.js, Vaadin, GWT und klassischen Webtechnologien aufbauen und mich schnell projektbezogen einarbeiten. ";
        }
        return "";
    }

    private List<String> legacyTechnologies(String text) {
        List<String> technologies = new ArrayList<>();
        addIfPresent(technologies, text, "Swing/SwingX", "swing", "swingx");
        addIfPresent(technologies, text, "JSF/MyFaces/RichFaces/PrimeFaces", "jsf", "myfaces", "richfaces", "primefaces");
        addIfPresent(technologies, text, "JSP/JSTL/TagLibs", "jsp", "jstl", "taglib");
        addIfPresent(technologies, text, "Struts", "struts");
        addIfPresent(technologies, text, "Servlets", "servlet");
        addIfPresent(technologies, text, "EJB", "ejb");
        addIfPresent(technologies, text, "SOAP/JAX-WS", "soap", "jax-ws", "jaxws");
        addIfPresent(technologies, text, "WebSphere", "websphere");
        addIfPresent(technologies, text, "WebLogic", "weblogic");
        return technologies.stream().limit(4).toList();
    }

    private int fallbackScore(List<String> signals, String migrationSentence, String domainSentence) {
        int score = 72 + Math.min(signals.size() * 3, 18);
        if (!migrationSentence.isBlank()) {
            score += 5;
        }
        if (!domainSentence.isBlank()) {
            score += 5;
        }
        return Math.min(score, 95);
    }

    private String fallbackExplanation(List<String> signals, String migrationSentence, String domainSentence) {
        String base = signals.isEmpty()
                ? "Vorlaeufige Bewertung ohne aktiven AI-Provider. Die Rolle passt grundsaetzlich zu Java, Full-Stack- und Enterprise-Erfahrung."
                : "Vorlaeufige Bewertung ohne aktiven AI-Provider. Starke Ueberschneidungen: " + joinGerman(signals) + ".";
        if (!domainSentence.isBlank()) {
            base += " Passender Domaenenfit ist vorhanden.";
        }
        if (!migrationSentence.isBlank()) {
            return base + " Der beschriebene Migrationskontext erhoeht den Fit deutlich.";
        }
        return base;
    }

    private String joinGerman(List<String> values) {
        if (values.size() == 1) {
            return values.getFirst();
        }
        if (values.size() == 2) {
            return values.get(0) + " und " + values.get(1);
        }
        return String.join(", ", values.subList(0, values.size() - 1))
                + " und " + values.getLast();
    }

    private String jobText(JobOffer jobOffer) {
        return (safe(jobOffer.getTitle()) + " " + safe(jobOffer.getCompany()) + " "
                + safe(jobOffer.getLocation()) + " " + safe(jobOffer.getDescription()))
                .toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
