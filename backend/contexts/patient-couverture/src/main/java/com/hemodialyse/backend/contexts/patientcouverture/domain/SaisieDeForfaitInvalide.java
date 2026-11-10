package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.util.List;

/** La saisie d'un forfait est refusée : toutes ses anomalies sont rendues ensemble, le code d'abord, puis le libellé, puis le prix. */
public class SaisieDeForfaitInvalide extends RuntimeException {

    private final List<AnomalieDeSaisie> anomalies;

    public SaisieDeForfaitInvalide(List<AnomalieDeSaisie> anomalies) {
        super("Saisie de forfait invalide : " + anomalies.size() + " anomalie(s)");
        this.anomalies = List.copyOf(anomalies);
    }

    public List<AnomalieDeSaisie> anomalies() {
        return anomalies;
    }
}
