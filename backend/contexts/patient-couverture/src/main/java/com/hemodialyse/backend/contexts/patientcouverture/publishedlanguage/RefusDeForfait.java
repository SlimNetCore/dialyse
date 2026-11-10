package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.util.List;

/** Un refus de la gestion du catalogue de forfaits, avec son motif ; les anomalies ou le nombre de références l'expliquent. */
public class RefusDeForfait extends RuntimeException {

    public enum Motif {SAISIE_INVALIDE, DEJA_EXISTANT, INTROUVABLE, UTILISE}

    private final Motif motif;
    private final List<GestionDuCatalogueDeForfaits.Anomalie> anomalies;
    private final long references;

    private RefusDeForfait(Motif motif, String message, List<GestionDuCatalogueDeForfaits.Anomalie> anomalies, long references) {
        super(message);
        this.motif = motif;
        this.anomalies = List.copyOf(anomalies);
        this.references = references;
    }

    public static RefusDeForfait saisieInvalide(List<GestionDuCatalogueDeForfaits.Anomalie> anomalies) {
        return new RefusDeForfait(Motif.SAISIE_INVALIDE, "Saisie de forfait invalide", anomalies, 0);
    }

    public static RefusDeForfait dejaExistant(String message) {
        return new RefusDeForfait(Motif.DEJA_EXISTANT, message, List.of(), 0);
    }

    public static RefusDeForfait introuvable() {
        return new RefusDeForfait(Motif.INTROUVABLE, "Forfait introuvable dans ce centre", List.of(), 0);
    }

    public static RefusDeForfait utilise(long references) {
        return new RefusDeForfait(Motif.UTILISE, "Forfait encore utilisé (" + references + " référence(s))", List.of(), references);
    }

    public Motif motif() {
        return motif;
    }

    public List<GestionDuCatalogueDeForfaits.Anomalie> anomalies() {
        return anomalies;
    }

    /** Nombre de références (prises en charge, lignes de facture, séances) qui retiennent le forfait ; 0 hors du motif {@code UTILISE}. */
    public long references() {
        return references;
    }
}
