package com.hemodialyse.backend.application.supervision;

/**
 * Statistiques cumulées d'une requête SQL normalisée (les valeurs sont remplacées par {@code $1}, {@code $2}… : le
 * texte ne contient donc aucune donnée de patient).
 *
 * @param id           identifiant PostgreSQL de la requête normalisée (peut être vide pour un ordre utilitaire)
 * @param requete      texte SQL normalisé, tronqué pour l'affichage
 * @param appels       nombre d'exécutions depuis la dernière réinitialisation
 * @param tempsTotalMs temps cumulé passé à l'exécuter, en millisecondes
 * @param tempsMoyenMs durée moyenne d'une exécution, en millisecondes
 * @param tempsMaxMs   exécution la plus longue, en millisecondes
 * @param lignes       lignes lues ou renvoyées, cumulées
 */
public record RequeteStatistique(
        String id,
        String requete,
        long appels,
        double tempsTotalMs,
        double tempsMoyenMs,
        double tempsMaxMs,
        long lignes
) {
}
