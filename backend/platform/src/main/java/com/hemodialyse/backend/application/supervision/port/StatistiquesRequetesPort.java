package com.hemodialyse.backend.application.supervision.port;

import com.hemodialyse.backend.application.supervision.RequeteStatistique;
import com.hemodialyse.backend.application.supervision.StatutStatistiques;
import com.hemodialyse.backend.application.supervision.TriRequetes;
import com.hemodialyse.backend.domain.shared.PagedResult;

/**
 * Port de sortie : statistiques d'exécution des requêtes de la base. L'adaptateur PostgreSQL lit
 * {@code pg_stat_statements} ; aucune dépendance à un moteur de base dans l'application.
 */
public interface StatistiquesRequetesPort {

    StatutStatistiques statut();

    /**
     * Requêtes de la base courante classées du plus coûteux au moins coûteux (page 0-indexée).
     */
    PagedResult<RequeteStatistique> classement(TriRequetes tri, int page, int size);

    /**
     * Remet les compteurs à zéro (avant/après une optimisation).
     */
    void reinitialiser();
}
