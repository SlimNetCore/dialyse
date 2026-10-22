package com.hemodialyse.backend.contexts.absencespatients.domain;

import java.util.UUID;

/**
 * Identifiants typés du contexte (pas de primitive obsession). {@link CentreId} est destiné au shared-kernel cible
 * (ADR 0010) ; copie locale tant que le module {@code contexts/shared-kernel} n'existe pas.
 */
public final class Identifiants {

    private Identifiants() {
    }

    private static UUID requis(UUID valeur, String nom) {
        if (valeur == null) throw new IllegalArgumentException(nom + " requis");
        return valeur;
    }

    /** Identité d'un centre de dialyse. */
    public record CentreId(UUID valeur) {
        public CentreId {
            requis(valeur, "Centre");
        }
    }

    /** Identité d'un patient (contexte Patient et couverture, référencé par identité). */
    public record PatientId(UUID valeur) {
        public PatientId {
            requis(valeur, "Patient");
        }
    }

    /** Identité d'une absence de patient. */
    public record AbsenceId(UUID valeur) {
        public AbsenceId {
            requis(valeur, "Absence");
        }
    }

    /** Identité de l'utilisateur qui écrit (contexte Identité et accès, référencé par identité ; tracé par l'absence). */
    public record UtilisateurId(UUID valeur) {
        public UtilisateurId {
            requis(valeur, "Utilisateur");
        }
    }

    /** Identité d'un forfait (contexte Patient et couverture, référencé par identité). */
    public record ForfaitId(UUID valeur) {
        public ForfaitId {
            requis(valeur, "Forfait");
        }
    }
}
