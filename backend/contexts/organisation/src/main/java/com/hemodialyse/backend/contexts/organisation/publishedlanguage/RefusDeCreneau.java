package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.List;

/** Refus d'une commande sur les créneaux, dans le langage publié. */
public class RefusDeCreneau extends RuntimeException {

    public enum Motif { SAISIE_INVALIDE, DEJA_EXISTANT, INTROUVABLE, CONTROLE_D_USAGE_IMPOSSIBLE }

    private final Motif motif;
    private final List<GestionDesCreneaux.Anomalie> anomalies;

    private RefusDeCreneau(Motif motif, String message, List<GestionDesCreneaux.Anomalie> anomalies) {
        super(message);
        this.motif = motif;
        this.anomalies = List.copyOf(anomalies);
    }

    public static RefusDeCreneau saisieInvalide(List<GestionDesCreneaux.Anomalie> anomalies) {
        return new RefusDeCreneau(Motif.SAISIE_INVALIDE, "Saisie de créneau invalide", anomalies);
    }

    public static RefusDeCreneau dejaExistant(String message) {
        return new RefusDeCreneau(Motif.DEJA_EXISTANT, message, List.of());
    }

    public static RefusDeCreneau introuvable() {
        return new RefusDeCreneau(Motif.INTROUVABLE, "Créneau introuvable", List.of());
    }

    /** D10 : le contrôle d'usage a échoué, la suppression est refusée (jamais « zéro usage » supposé). */
    public static RefusDeCreneau controleDUsageImpossible() {
        return new RefusDeCreneau(Motif.CONTROLE_D_USAGE_IMPOSSIBLE, "Contrôle d'usage du créneau impossible", List.of());
    }

    public Motif motif() {
        return motif;
    }

    public List<GestionDesCreneaux.Anomalie> anomalies() {
        return anomalies;
    }
}
