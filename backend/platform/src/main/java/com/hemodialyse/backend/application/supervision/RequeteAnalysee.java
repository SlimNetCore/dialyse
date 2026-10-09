package com.hemodialyse.backend.application.supervision;

/**
 * Une requête mesurée, avec sa part du temps total de la base et sa gravité.
 *
 * @param statistique       mesures brutes
 * @param partTempsTotalPct part (en %) du temps cumulé de toutes les requêtes
 * @param niveau            gravité (voir {@link NiveauRequete})
 */
public record RequeteAnalysee(RequeteStatistique statistique, double partTempsTotalPct, NiveauRequete niveau) {
}
