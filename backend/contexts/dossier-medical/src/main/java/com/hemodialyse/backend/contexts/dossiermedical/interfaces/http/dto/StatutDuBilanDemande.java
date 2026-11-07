package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDuBilanPreGreffe;

import java.util.UUID;

/** Corps du changement de statut du bilan pré-greffe (mêmes champs que le legacy). */
public record StatutDuBilanDemande(UUID centerId, String statut) {
    public StatutDuBilanPreGreffe statutLu() {
        return Enumerations.obligatoire(StatutDuBilanPreGreffe.class, statut, "statut");
    }
}
