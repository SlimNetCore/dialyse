package com.hemodialyse.backend.contexts.planification.domain;

import java.util.UUID;

/**
 * Identifiants typés du contexte (pas de primitive obsession). {@link CentreId} est destiné au shared-kernel cible
 * (ADR 0010) ; copie locale tant que le module {@code contexts/shared-kernel} n'existe pas. Salle, créneau, patient et
 * infirmier sont des références par identité vers d'autres contextes (Organisation, Patient et couverture).
 */
public final class Identifiants {

    private Identifiants() {
    }

    private static UUID requis(UUID valeur, String nom) {
        if (valeur == null) throw new SaisieInvalide(nom + " requis");
        return valeur;
    }

    /** Identité d'un centre de dialyse. */
    public record CentreId(UUID valeur) {
        public CentreId {
            requis(valeur, "Centre");
        }
    }

    /** Salle du centre (référence conformiste à Organisation). */
    public record SalleId(UUID valeur) {
        public SalleId {
            requis(valeur, "Salle");
        }
    }

    /** Créneau du centre (référence conformiste à Organisation). */
    public record CreneauId(UUID valeur) {
        public CreneauId {
            requis(valeur, "Créneau");
        }
    }

    /** Patient (contexte Patient et couverture, référencé par identité). */
    public record PatientId(UUID valeur) {
        public PatientId {
            requis(valeur, "Patient");
        }
    }

    /** Infirmier du centre. */
    public record InfirmierId(UUID valeur) {
        public InfirmierId {
            requis(valeur, "Infirmier");
        }
    }
}
