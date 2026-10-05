package com.hemodialyse.backend.domain.seance.model;

import java.time.LocalDate;
import java.util.Set;

/**
 * Critères de recherche de l'historique des séances : période, statuts, texte libre (nom, prénom ou code du patient,
 * tous les mots doivent correspondre) et tri. Tous les critères sont facultatifs.
 *
 * @param from     première date incluse (null = sans borne)
 * @param to       dernière date incluse (null = sans borne)
 * @param statuses statuts retenus (vide = tous)
 * @param text     texte libre (null ou vide = sans filtre)
 * @param sort     colonne de tri ({@link Sort#DATE} par défaut)
 * @param desc     tri décroissant
 */
public record SeanceSearch(LocalDate from, LocalDate to, Set<SeanceStatus> statuses, String text, Sort sort,
                           boolean desc) {

    public SeanceSearch {
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        sort = sort == null ? Sort.DATE : sort;
        text = text == null ? null : text.trim();
    }

    /**
     * Aucun filtre, tri par date décroissante.
     */
    public static SeanceSearch all() {
        return new SeanceSearch(null, null, Set.of(), null, Sort.DATE, true);
    }

    /**
     * Colonnes sur lesquelles l'historique peut être trié (liste blanche, toutes calculables en base).
     */
    public enum Sort {
        DATE, PATIENT, CODE, STATUS, CREATED;

        /**
         * Colonne de tri depuis son nom côté client ; inconnue ou vide : {@link #DATE}.
         */
        public static Sort parse(String raw) {
            if (raw == null) return DATE;
            return switch (raw.trim().toLowerCase()) {
                case "patient", "patientnom" -> PATIENT;
                case "code", "patientcode" -> CODE;
                case "status", "statut" -> STATUS;
                case "createdat", "created" -> CREATED;
                default -> DATE;
            };
        }
    }
}
