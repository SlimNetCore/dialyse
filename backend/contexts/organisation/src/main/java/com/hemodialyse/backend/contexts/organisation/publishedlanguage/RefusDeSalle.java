package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.List;

/** Refus d'une commande sur les salles, dans le langage publié. */
public class RefusDeSalle extends RuntimeException {

    public enum Motif { SAISIE_INVALIDE, DEJA_EXISTANTE, INTROUVABLE, CONTROLE_D_USAGE_IMPOSSIBLE }

    private final Motif motif;
    private final List<GestionDesSalles.Anomalie> anomalies;

    private RefusDeSalle(Motif motif, String message, List<GestionDesSalles.Anomalie> anomalies) {
        super(message);
        this.motif = motif;
        this.anomalies = List.copyOf(anomalies);
    }

    public static RefusDeSalle saisieInvalide(List<GestionDesSalles.Anomalie> anomalies) {
        return new RefusDeSalle(Motif.SAISIE_INVALIDE, "Saisie de salle invalide", anomalies);
    }

    public static RefusDeSalle dejaExistante(String message) {
        return new RefusDeSalle(Motif.DEJA_EXISTANTE, message, List.of());
    }

    public static RefusDeSalle introuvable() {
        return new RefusDeSalle(Motif.INTROUVABLE, "Salle introuvable", List.of());
    }

    /** D10 : le contrôle d'usage a échoué, la suppression est refusée (jamais « zéro usage » supposé). */
    public static RefusDeSalle controleDUsageImpossible() {
        return new RefusDeSalle(Motif.CONTROLE_D_USAGE_IMPOSSIBLE, "Contrôle d'usage de la salle impossible", List.of());
    }

    public Motif motif() {
        return motif;
    }

    public List<GestionDesSalles.Anomalie> anomalies() {
        return anomalies;
    }
}
