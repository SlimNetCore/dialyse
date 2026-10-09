package com.hemodialyse.backend.application.supervision;

/**
 * Statistiques cumulées d'une requête SQL normalisée (les valeurs sont remplacées par {@code $1}, {@code $2}… : le
 * texte ne contient donc aucune donnée de patient).
 *
 * @param id               identifiant PostgreSQL de la requête normalisée (peut être vide pour un ordre utilitaire)
 * @param requete          texte SQL normalisé, tronqué pour l'affichage
 * @param appels           nombre d'exécutions depuis la dernière réinitialisation
 * @param tempsTotalMs     temps cumulé passé à l'exécuter, en millisecondes
 * @param tempsMoyenMs     durée moyenne d'une exécution, en millisecondes
 * @param tempsMaxMs       exécution la plus longue, en millisecondes
 * @param lignes           lignes lues ou renvoyées, cumulées
 * @param blocsCache       blocs trouvés dans le cache mémoire de PostgreSQL
 * @param blocsDisque      blocs lus sur le disque (absents du cache)
 * @param blocsTemporaires blocs écrits dans des fichiers temporaires (tri ou jointure qui déborde de {@code work_mem})
 */
public record RequeteStatistique(
        String id,
        String requete,
        long appels,
        double tempsTotalMs,
        double tempsMoyenMs,
        double tempsMaxMs,
        long lignes,
        long blocsCache,
        long blocsDisque,
        long blocsTemporaires
) {
    /**
     * Lignes lues ou renvoyées, en moyenne, par exécution.
     */
    public double lignesParAppel() {
        return appels <= 0 ? 0 : (double) lignes / appels;
    }

    /**
     * Part (en %) des lectures servies par le cache mémoire ; 100 quand rien n'a été lu.
     */
    public double tauxCachePct() {
        long total = blocsCache + blocsDisque;
        return total <= 0 ? 100 : blocsCache * 100.0 / total;
    }
}
