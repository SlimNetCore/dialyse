package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.PresenceDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDeLaPresence;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Le planning de présence imprimable d'une semaine, au format du modèle de document du centre (RG-INF-061). Formats servis : PDF (par
 * défaut), HTML (le PDF embarqué en base64 dans une page) et EXCEL (alias XLS, XLSX : toujours un classeur xlsx). Tout autre format
 * (CSV, RTF, inconnu) est refusé en 400 (KB-PLN-51, décision du 27/10/2026). Lecture seule : rien n'est créé chez le centre.
 */
public class ImprimerLaPresence {

    public enum Format {
        PDF, HTML, EXCEL;

        static Format depuis(String format) {
            if (format == null || format.isBlank()) return PDF;
            return switch (format.trim().toUpperCase(Locale.ROOT)) {
                case "PDF" -> PDF;
                case "HTML" -> HTML;
                case "EXCEL", "XLS", "XLSX" -> EXCEL;
                default -> throw new SaisieInvalide("Format d'impression non supporté : " + format.trim());
            };
        }
    }

    public record Impression(byte[] contenu, Format format, LocalDate debutDeSemaine) {
    }

    private final ImpressionDeLaPresence impression;
    private final HorlogeDesCentres horloge;

    public ImprimerLaPresence(ImpressionDeLaPresence impression, HorlogeDesCentres horloge) {
        this.impression = impression;
        this.horloge = horloge;
    }

    /** @param date un jour de la semaine voulue, ou {@code null} pour la semaine courante du centre */
    public Impression executer(CentreId centre, LocalDate date) {
        LocalDate debut = PresenceDeLaSemaine.debutDeSemaine(date != null ? date : horloge.aujourdhui(centre));
        ImpressionDeLaPresence.Document document = impression.imprimer(centre.valeur(), debut);
        return new Impression(document.contenu(), Format.depuis(document.format()), debut);
    }
}
