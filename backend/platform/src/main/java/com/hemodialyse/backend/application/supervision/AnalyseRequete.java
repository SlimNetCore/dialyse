package com.hemodialyse.backend.application.supervision;

import java.util.List;

/**
 * Résultat de l'analyse du plan d'exécution d'une requête.
 *
 * @param disponible        le plan a pu être établi
 * @param raison            sinon pourquoi : {@code NON_SELECT} ou {@code PLAN_IMPOSSIBLE}
 * @param requete           texte SQL normalisé analysé
 * @param planTexte         plan d'exécution lisible
 * @param coutTotal         coût estimé du plan par PostgreSQL (unité arbitraire, utile pour comparer)
 * @param balayagesComplets tables lues en entier par le plan, avec le verdict sur l'opportunité d'un index
 * @param indexUtilises     nombre de parcours d'index dans le plan
 */
public record AnalyseRequete(
        boolean disponible,
        String raison,
        String requete,
        String planTexte,
        double coutTotal,
        List<BalayageComplet> balayagesComplets,
        int indexUtilises
) {

    public static AnalyseRequete indisponible(String requete, String raison) {
        return new AnalyseRequete(false, raison, requete, null, 0, List.of(), 0);
    }

    public enum Verdict {
        /**
         * Un index sur les colonnes filtrées éviterait la lecture complète.
         */
        INDEX_RECOMMANDE,
        /**
         * La table est petite : lire tout est le plus rapide, un index n'apporterait rien.
         */
        TABLE_PETITE,
        /**
         * Un index existe déjà sur la première colonne filtrée : le plan générique ne reflète pas forcément le réel.
         */
        INDEX_PRESENT,
        /**
         * Lecture complète voulue (agrégat, export) : pré-calculer ou mettre en cache plutôt qu'indexer.
         */
        SANS_FILTRE
    }

    /**
     * Une table lue en entier par le plan.
     *
     * @param table          table concernée
     * @param filtre         condition appliquée à la lecture (vide si la table est lue sans filtre)
     * @param colonnes       colonnes filtrées, égalités d'abord (ordre utile pour un index)
     * @param lignesTable    lignes estimées de la table
     * @param indexExistants définitions des index déjà présents sur la table
     * @param verdict        conclusion
     * @param indexSuggere   ordre {@code CREATE INDEX} à étudier, seulement pour {@link Verdict#INDEX_RECOMMANDE}
     */
    public record BalayageComplet(String table, String filtre, List<String> colonnes, long lignesTable,
                                  List<String> indexExistants, Verdict verdict, String indexSuggere) {
    }
}
