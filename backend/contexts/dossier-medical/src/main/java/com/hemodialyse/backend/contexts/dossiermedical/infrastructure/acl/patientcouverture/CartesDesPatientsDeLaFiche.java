package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.dossiermedical.application.CartesDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/** ACL : la carte du patient vient du langage publié de Patient et couverture (jamais de SQL sur la table {@code patients}). */
public class CartesDesPatientsDeLaFiche implements CartesDesPatients {
    private final FichesDesPatients fiches;

    public CartesDesPatientsDeLaFiche(FichesDesPatients fiches) {
        this.fiches = fiches;
    }

    @Override
    public Optional<Carte> duPatient(CentreId centre, UUID patient) {
        return fiches.carte(centre.valeur(), patient).map(c -> new Carte(c.code(), c.nom(), c.prenom(), c.groupeSanguin()));
    }
}
