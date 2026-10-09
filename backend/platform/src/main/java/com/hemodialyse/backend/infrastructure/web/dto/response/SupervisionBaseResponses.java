package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.application.supervision.RequeteAnalysee;
import com.hemodialyse.backend.application.supervision.StatutStatistiques;

import java.time.Instant;

/**
 * Réponses de l'écran « Performance de la base » du propriétaire.
 */
public final class SupervisionBaseResponses {

    private SupervisionBaseResponses() {
    }

    public record Statut(boolean disponible, String raison, Instant reinitialiseLe, double tempsTotalMs) {
        public static Statut from(StatutStatistiques s) {
            return new Statut(s.disponible(), s.raison(), s.reinitialiseLe(), s.tempsTotalMs());
        }
    }

    public record Requete(String id, String requete, long appels, double tempsTotalMs, double tempsMoyenMs,
                          double tempsMaxMs, long lignes, double partTempsTotalPct, String niveau) {
        public static Requete from(RequeteAnalysee a) {
            var s = a.statistique();
            return new Requete(s.id(), s.requete(), s.appels(), s.tempsTotalMs(), s.tempsMoyenMs(), s.tempsMaxMs(),
                    s.lignes(), a.partTempsTotalPct(), a.niveau().name());
        }
    }
}
