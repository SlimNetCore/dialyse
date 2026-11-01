package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.application.LectureDesPreferences.LigneDePatient;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;

import java.util.List;
import java.util.UUID;

public record LignePatientReponse(UUID patientId, String nom, List<JourDeLaSemaine> jours, UUID creneauActuelId,
                                  PreferenceReponse preference) {
    public static LignePatientReponse depuis(LigneDePatient l) {
        return new LignePatientReponse(l.patient().valeur(), l.nom(), l.jours().stream().sorted().toList(),
                l.creneauActuel() == null ? null : l.creneauActuel().valeur(), PreferenceReponse.depuis(l.preference()));
    }
}
