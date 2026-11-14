package com.hemodialyse.backend.contexts.seance.infrastructure.acl.gmao;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.FichesDesGenerateurs;
import com.hemodialyse.backend.contexts.seance.application.GenerateursDesPatients;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/**
 * Générateur du patient (SE7f, ADR 0100 ; LEC-PLN-GMAO, ADR 0179) : le générateur est un équipement de la GMAO (source de vérité unique),
 * lu par le langage publié {@link FichesDesGenerateurs} (code, fabricant et statut, bornés au centre, hors équipement supprimé). Plus aucun
 * SQL sur la table des équipements.
 */
public class GenerateursDesPatientsParLaGmao implements GenerateursDesPatients {

    private final FichesDesGenerateurs fiches;

    public GenerateursDesPatientsParLaGmao(FichesDesGenerateurs fiches) {
        this.fiches = fiches;
    }

    @Override
    public Optional<Generateur> duCentre(CentreId centre, UUID equipement) {
        return fiches.duCentre(centre.valeur(), equipement).map(f -> new Generateur(f.id(), f.code(), f.fabricant(), f.statut()));
    }
}
