package com.hemodialyse.backend.contexts.seance.domain;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Critères de l'historique des séances : période, statuts, texte libre (tous les mots doivent correspondre au nom, au prénom ou au
 * code du patient), déverrouillage pour régularisation et tri. Tous facultatifs.
 *
 * @param du              première date incluse (nulle : sans borne)
 * @param au              dernière date incluse (nulle : sans borne)
 * @param statuts         statuts retenus (vide : tous)
 * @param texte           texte libre (nul ou vide : sans filtre)
 * @param ordre           colonne de tri ({@link OrdreDeTri#DATE} par défaut)
 * @param decroissant     tri décroissant
 * @param deverrouillee   vrai : seulement les séances déverrouillées pour régularisation ; faux : seulement les autres ; nul : tout
 */
public record RechercheDeSeances(LocalDate du, LocalDate au, Set<StatutDeSeance> statuts, String texte, OrdreDeTri ordre,
                                 boolean decroissant, Boolean deverrouillee) {

    public RechercheDeSeances {
        if (du != null && au != null && du.isAfter(au)) throw new SaisieInvalide("La date de debut doit preceder la date de fin");
        statuts = statuts == null || statuts.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(statuts));
        ordre = ordre == null ? OrdreDeTri.DATE : ordre;
        texte = texte == null ? null : texte.trim();
    }

    /** Aucun filtre, tri par date décroissante. */
    public static RechercheDeSeances toutes() {
        return new RechercheDeSeances(null, null, Set.of(), null, OrdreDeTri.DATE, true, null);
    }

    /** Lit une liste de statuts séparés par des virgules (casse et espaces ignorés) ; un statut inconnu est refusé (400). */
    public static Set<StatutDeSeance> lireLesStatuts(String liste) {
        Set<StatutDeSeance> lus = new LinkedHashSet<>();
        if (liste == null || liste.isBlank()) return lus;
        for (String brut : liste.split(",")) {
            try {
                lus.add(StatutDeSeance.valueOf(brut.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new SaisieInvalide("Invalid status: " + brut.trim());
            }
        }
        return lus;
    }

    /** Colonnes de tri de l'historique (liste blanche, toutes calculables en base). */
    public enum OrdreDeTri {
        DATE, PATIENT, CODE, STATUT, CREATION;

        /** Colonne de tri depuis son nom côté client ; inconnue ou vide : {@link #DATE}. */
        public static OrdreDeTri lire(String brut) {
            if (brut == null) return DATE;
            return switch (brut.trim().toLowerCase()) {
                case "patient", "patientnom" -> PATIENT;
                case "code", "patientcode" -> CODE;
                case "status", "statut" -> STATUT;
                case "createdat", "created" -> CREATION;
                default -> DATE;
            };
        }
    }
}
