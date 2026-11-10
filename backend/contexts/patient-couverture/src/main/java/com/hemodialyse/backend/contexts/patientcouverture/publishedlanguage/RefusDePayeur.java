package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.util.List;

/** Un refus de la gestion de la chaîne des payeurs, avec son motif ; les anomalies ou le nombre de références l'expliquent. */
public class RefusDePayeur extends RuntimeException {

    public enum Motif {SAISIE_INVALIDE, DEJA_EXISTANT, INTROUVABLE, UTILISE}

    private final Motif motif;
    private final List<GestionDesPayeurs.Anomalie> anomalies;
    private final long references;

    private RefusDePayeur(Motif motif, String message, List<GestionDesPayeurs.Anomalie> anomalies, long references) {
        super(message);
        this.motif = motif;
        this.anomalies = List.copyOf(anomalies);
        this.references = references;
    }

    public static RefusDePayeur saisieInvalide(List<GestionDesPayeurs.Anomalie> anomalies) {
        return new RefusDePayeur(Motif.SAISIE_INVALIDE, "Saisie de payeur invalide", anomalies, 0);
    }

    public static RefusDePayeur dejaExistant(String message) {
        return new RefusDePayeur(Motif.DEJA_EXISTANT, message, List.of(), 0);
    }

    public static RefusDePayeur introuvable() {
        return new RefusDePayeur(Motif.INTROUVABLE, "Payeur introuvable dans ce centre", List.of(), 0);
    }

    public static RefusDePayeur utilise(long references) {
        return new RefusDePayeur(Motif.UTILISE, "Payeur encore utilisé (" + references + " référence(s))", List.of(), references);
    }

    public Motif motif() {
        return motif;
    }

    public List<GestionDesPayeurs.Anomalie> anomalies() {
        return anomalies;
    }

    /** Nombre de références (agences, centres payeurs, patients) qui retiennent le payeur ; 0 hors du motif {@code UTILISE}. */
    public long references() {
        return references;
    }
}
