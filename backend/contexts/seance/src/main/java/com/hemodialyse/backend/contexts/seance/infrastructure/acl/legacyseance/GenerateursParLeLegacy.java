package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.seance.application.GenerateursDesPatients;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.GenerateursFournis;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/** ACL des générateurs : traduit le langage publié {@link GenerateursFournis}. */
public class GenerateursParLeLegacy implements GenerateursDesPatients {

    private final GenerateursFournis generateurs;

    public GenerateursParLeLegacy(GenerateursFournis generateurs) {
        this.generateurs = generateurs;
    }

    @Override
    public Optional<Generateur> duCentre(CentreId centre, UUID equipement) {
        return generateurs.duCentre(centre.valeur(), equipement).map(g -> new Generateur(g.id(), g.code(), g.fabricant(), g.statut()));
    }
}
