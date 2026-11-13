package com.hemodialyse.backend.contexts.organisation.domain;

import java.util.List;

/** Le nom est absent ou un champ saisi est trop long. */
public class SaisieDeMedecinInvalide extends RuntimeException {
    private final List<AnomalieDeSaisie> anomalies;

    public SaisieDeMedecinInvalide(List<AnomalieDeSaisie> anomalies) {
        super("Saisie de médecin invalide");
        this.anomalies = List.copyOf(anomalies);
    }

    public List<AnomalieDeSaisie> anomalies() {
        return anomalies;
    }
}
