package com.hemodialyse.backend.application.supervision;

import java.util.List;

/**
 * Une requête mesurée, avec sa part du temps total de la base, sa gravité et les pistes pour l'améliorer.
 *
 * @param statistique       mesures brutes
 * @param partTempsTotalPct part (en %) du temps cumulé de toutes les requêtes
 * @param niveau            gravité (voir {@link NiveauRequete})
 * @param conseils          pistes d'amélioration (voir {@link ConseilsRequete}), vides pour une requête saine
 */
public record RequeteAnalysee(RequeteStatistique statistique, double partTempsTotalPct, NiveauRequete niveau,
                              List<Conseil> conseils) {
}
