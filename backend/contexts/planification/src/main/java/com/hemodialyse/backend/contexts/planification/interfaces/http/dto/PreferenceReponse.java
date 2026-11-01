package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.PreferenceDePlanification;

import java.util.UUID;

public record PreferenceReponse(UUID patientId, UUID creneauPrefereId, Integer seancesParSemaine,
                                boolean joursAChoisir) {
    public static PreferenceReponse depuis(PreferenceDePlanification p) {
        return new PreferenceReponse(p.patient().valeur(), p.creneauPrefere() == null ? null : p.creneauPrefere().valeur(),
                p.seancesParSemaine(), p.joursAChoisir());
    }
}
