package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.dossiermedical.application.IdentitesDesPatients;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/** ACL : l'état civil du patient vient du langage publié de Patient et couverture (jamais de SQL sur la table {@code patients}). */
public class IdentitesDesPatientsDeLaFiche implements IdentitesDesPatients {
    private final FichesDesPatients fiches;

    public IdentitesDesPatientsDeLaFiche(FichesDesPatients fiches) {
        this.fiches = fiches;
    }

    @Override
    public Optional<Identite> duPatient(CentreId centre, PatientId patient) {
        return fiches.identite(centre.valeur(), patient.valeur())
                .map(i -> new Identite(i.nom(), i.prenom(), i.sexe(), i.dateNaissance(), i.numeroAssurance()));
    }
}
