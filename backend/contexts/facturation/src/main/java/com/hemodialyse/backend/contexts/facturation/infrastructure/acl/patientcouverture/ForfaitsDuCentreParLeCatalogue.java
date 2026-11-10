package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/**
 * ACL vers le catalogue de forfaits de Patient et couverture (RF2, ADR 0124) : remplace la lecture SQL de la table {@code forfait}. Un
 * forfait du centre avec son libellé et son prix hors taxe ; inconnu ou d'un autre centre : vide. Un forfait sans prix vaut zéro
 * ({@link ForfaitApplique}).
 */
public class ForfaitsDuCentreParLeCatalogue implements ForfaitsDuCentre {

    private final com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ForfaitsDuCentre catalogue;

    public ForfaitsDuCentreParLeCatalogue(com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ForfaitsDuCentre catalogue) {
        this.catalogue = catalogue;
    }

    @Override
    public Optional<ForfaitApplique> forfait(CentreId centre, UUID forfait) {
        return catalogue.trouver(centre.valeur(), forfait).map(f -> new ForfaitApplique(f.id(), f.libelle(), f.prix()));
    }
}
