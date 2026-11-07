package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.dossiermedical.application.PatientsDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * ACL vers le langage publié de Patient et couverture ({@code patientcouverture.v1}) : un patient existe pour le centre s'il y est
 * enregistré. Remplace la lecture SQL directe de {@code patients} (comportement inchangé).
 */
public class PatientsDuCentreDeLaFiche implements PatientsDuCentre {

    private final FichesDesPatients fiches;

    public PatientsDuCentreDeLaFiche(FichesDesPatients fiches) {
        this.fiches = fiches;
    }

    @Override
    public boolean existe(CentreId centre, PatientId patient) {
        return fiches.existe(centre.valeur(), patient.valeur());
    }
}
