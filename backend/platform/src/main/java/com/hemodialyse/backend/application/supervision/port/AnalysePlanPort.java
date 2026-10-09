package com.hemodialyse.backend.application.supervision.port;

import java.util.List;
import java.util.Optional;

/**
 * Port de sortie : plan d'exécution d'une requête mesurée, sans l'exécuter, et métadonnées des tables concernées.
 * Le texte SQL n'est jamais fourni par l'appelant : il est relu côté base à partir de l'identifiant mesuré.
 */
public interface AnalysePlanPort {

    /**
     * Plan générique (paramètres non renseignés) de la requête mesurée.
     *
     * @return vide si l'identifiant n'est pas (ou plus) dans les mesures
     */
    Optional<PlanGenerique> planGenerique(String queryId);

    /**
     * Définitions SQL des index d'une table.
     */
    List<String> definitionsIndex(String table);

    /**
     * Nombre de lignes estimé d'une table (statistiques du planificateur).
     */
    long lignesEstimees(String table);

    /**
     * @param requete            texte SQL normalisé analysé
     * @param json               plan au format JSON, {@code null} si l'analyse est impossible
     * @param texte              plan lisible, {@code null} si l'analyse est impossible
     * @param raisonIndisponible {@code NON_SELECT} (seules les lectures sont analysées) ou {@code PLAN_IMPOSSIBLE}
     *                           (PostgreSQL ne sait pas planifier le texte normalisé) ; {@code null} si le plan existe
     */
    record PlanGenerique(String requete, String json, String texte, String raisonIndisponible) {

        public static PlanGenerique indisponible(String requete, String raison) {
            return new PlanGenerique(requete, null, null, raison);
        }
    }
}
