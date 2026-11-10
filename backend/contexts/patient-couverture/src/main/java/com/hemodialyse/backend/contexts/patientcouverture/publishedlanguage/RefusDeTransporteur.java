package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.util.List;

/** Un refus de la gestion des transporteurs, avec son motif ; les anomalies ou le nombre de références l'expliquent. */
public class RefusDeTransporteur extends RuntimeException {

    public enum Motif {SAISIE_INVALIDE, DEJA_EXISTANT, INTROUVABLE, UTILISE}

    private final Motif motif;
    private final List<GestionDesTransporteurs.Anomalie> anomalies;
    private final long references;

    private RefusDeTransporteur(Motif motif, String message, List<GestionDesTransporteurs.Anomalie> anomalies, long references) {
        super(message);
        this.motif = motif;
        this.anomalies = List.copyOf(anomalies);
        this.references = references;
    }

    public static RefusDeTransporteur saisieInvalide(List<GestionDesTransporteurs.Anomalie> anomalies) {
        return new RefusDeTransporteur(Motif.SAISIE_INVALIDE, "Saisie de transporteur invalide", anomalies, 0);
    }

    public static RefusDeTransporteur dejaExistant(String message) {
        return new RefusDeTransporteur(Motif.DEJA_EXISTANT, message, List.of(), 0);
    }

    public static RefusDeTransporteur introuvable() {
        return new RefusDeTransporteur(Motif.INTROUVABLE, "Transporteur introuvable dans ce centre", List.of(), 0);
    }

    public static RefusDeTransporteur utilise(long references) {
        return new RefusDeTransporteur(Motif.UTILISE, "Transporteur encore utilisé (" + references + " référence(s))", List.of(),
                references);
    }

    public Motif motif() {
        return motif;
    }

    public List<GestionDesTransporteurs.Anomalie> anomalies() {
        return anomalies;
    }

    /** Nombre de références (patients) qui retiennent le transporteur ; 0 hors du motif {@code UTILISE}. */
    public long references() {
        return references;
    }
}
