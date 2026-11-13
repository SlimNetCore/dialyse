package com.hemodialyse.backend.contexts.organisation.domain;

import java.util.List;

/** Le code, le nom, l'isolement ou la capacité saisi est absent, trop long ou illisible. */
public class SaisieDeSalleInvalide extends RuntimeException {
    private final List<AnomalieDeSaisie> anomalies;

    public SaisieDeSalleInvalide(List<AnomalieDeSaisie> anomalies) {
        super("Saisie de salle invalide");
        this.anomalies = List.copyOf(anomalies);
    }

    public List<AnomalieDeSaisie> anomalies() {
        return anomalies;
    }
}
