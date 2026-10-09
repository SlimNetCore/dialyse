package com.hemodialyse.backend.application.supervision;

import java.time.Instant;

/**
 * État de la mesure des requêtes.
 *
 * @param disponible     la mesure est exploitable
 * @param raison         si elle ne l'est pas : {@code BASE_NON_POSTGRESQL} (développement H2), {@code EXTENSION_ABSENTE}
 *                       (migration V30 non appliquée) ou {@code PRELOAD_ABSENT} (serveur démarré sans
 *                       {@code shared_preload_libraries=pg_stat_statements}) ; {@code null} sinon
 * @param reinitialiseLe dernière remise à zéro des compteurs, si PostgreSQL la fournit
 * @param tempsTotalMs   temps cumulé de toutes les requêtes mesurées (base de calcul des pourcentages)
 */
public record StatutStatistiques(boolean disponible, String raison, Instant reinitialiseLe, double tempsTotalMs) {

    public static StatutStatistiques indisponible(String raison) {
        return new StatutStatistiques(false, raison, null, 0);
    }
}
