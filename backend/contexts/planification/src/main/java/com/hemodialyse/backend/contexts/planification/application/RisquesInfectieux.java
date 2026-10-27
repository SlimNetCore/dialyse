package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;

/**
 * Port sortant vers le Dossier médical : le risque infectieux d'un patient du centre (RG-PLN-012 : la dernière sérologie d'au moins
 * un marqueur AgHBs, anti-VHC, ARN du VHC ou VIH est positive, départagée par date de prélèvement puis de saisie).
 */
public interface RisquesInfectieux {

    /** {@code SANS_RISQUE} pour un patient inconnu ou d'un autre centre. */
    CategorieDIsolement de(CentreId centre, PatientId patient);
}
