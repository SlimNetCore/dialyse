package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.List;

/** Refus d'une commande sur les médecins, dans le langage publié. */
public class RefusDeMedecin extends RuntimeException {

    public enum Motif { SAISIE_INVALIDE, DEJA_EXISTANT, INTROUVABLE }

    private final Motif motif;
    private final List<GestionDesMedecins.Anomalie> anomalies;

    private RefusDeMedecin(Motif motif, String message, List<GestionDesMedecins.Anomalie> anomalies) {
        super(message);
        this.motif = motif;
        this.anomalies = List.copyOf(anomalies);
    }

    public static RefusDeMedecin saisieInvalide(List<GestionDesMedecins.Anomalie> anomalies) {
        return new RefusDeMedecin(Motif.SAISIE_INVALIDE, "Saisie de médecin invalide", anomalies);
    }

    public static RefusDeMedecin dejaExistant(String message) {
        return new RefusDeMedecin(Motif.DEJA_EXISTANT, message, List.of());
    }

    public static RefusDeMedecin introuvable() {
        return new RefusDeMedecin(Motif.INTROUVABLE, "Médecin introuvable", List.of());
    }

    public Motif motif() {
        return motif;
    }

    public List<GestionDesMedecins.Anomalie> anomalies() {
        return anomalies;
    }
}
