package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.util.List;

/** La saisie d'un payeur est refusée : toutes ses anomalies sont rendues ensemble, dans l'ordre des champs du niveau. */
public class SaisieDePayeurInvalide extends RuntimeException {

    private final List<AnomalieDeSaisie> anomalies;

    public SaisieDePayeurInvalide(List<AnomalieDeSaisie> anomalies) {
        super("Saisie de payeur invalide : " + anomalies.size() + " anomalie(s)");
        this.anomalies = List.copyOf(anomalies);
    }

    public List<AnomalieDeSaisie> anomalies() {
        return anomalies;
    }
}
