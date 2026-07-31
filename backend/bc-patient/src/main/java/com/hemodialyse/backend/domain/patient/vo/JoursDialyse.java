package com.hemodialyse.backend.domain.patient.vo;

/** Value Object — Days of dialysis for the patient */
public record JoursDialyse(
    boolean dimanche, boolean lundi, boolean mardi,
    boolean mercredi, boolean jeudi, boolean vendredi, boolean samedi
) {
    public static JoursDialyse none() {
        return new JoursDialyse(false, false, false, false, false, false, false);
    }
}

