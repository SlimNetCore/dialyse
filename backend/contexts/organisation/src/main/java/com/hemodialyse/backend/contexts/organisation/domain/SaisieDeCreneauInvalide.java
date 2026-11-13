package com.hemodialyse.backend.contexts.organisation.domain;

import java.util.List;

/** Le code ou le libellé saisi est absent ou trop long. */
public class SaisieDeCreneauInvalide extends RuntimeException {
    private final List<AnomalieDeSaisie> anomalies;

    public SaisieDeCreneauInvalide(List<AnomalieDeSaisie> anomalies) {
        super("Saisie de créneau invalide");
        this.anomalies = List.copyOf(anomalies);
    }

    public List<AnomalieDeSaisie> anomalies() {
        return anomalies;
    }
}
