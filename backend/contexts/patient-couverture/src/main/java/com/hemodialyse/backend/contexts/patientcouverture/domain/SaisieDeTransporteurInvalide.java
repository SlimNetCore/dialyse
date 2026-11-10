package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.util.List;

/** La saisie d'un transporteur est refusée : toutes ses anomalies sont rendues ensemble, le nom d'abord. */
public class SaisieDeTransporteurInvalide extends RuntimeException {

    private final List<AnomalieDeSaisie> anomalies;

    public SaisieDeTransporteurInvalide(List<AnomalieDeSaisie> anomalies) {
        super("Saisie de transporteur invalide : " + anomalies.size() + " anomalie(s)");
        this.anomalies = List.copyOf(anomalies);
    }

    public List<AnomalieDeSaisie> anomalies() {
        return anomalies;
    }
}
