package com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.seance.domain.ForfaitDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitId;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/**
 * ACL vers le catalogue de forfaits de Patient et couverture (RF2, ADR 0124) : remplace la lecture SQL de la table {@code forfait}
 * (SE7f). Un forfait du centre par identifiant, avec son code, son libellé (devenu le nom) et son prix tels quels ; inconnu ou d'un
 * autre centre : vide. Le prix nul reste nul (il n'est pas converti en zéro ici).
 */
public class ForfaitsDuCentreParLeCatalogue implements ForfaitsDuCentre {

    private final com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ForfaitsDuCentre catalogue;

    public ForfaitsDuCentreParLeCatalogue(com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ForfaitsDuCentre catalogue) {
        this.catalogue = catalogue;
    }

    @Override
    public Optional<ForfaitDuCentre> trouver(CentreId centre, ForfaitId forfait) {
        return catalogue.trouver(centre.valeur(), forfait.valeur())
                .map(f -> new ForfaitDuCentre(new ForfaitId(f.id()), f.code(), f.libelle(), f.prix()));
    }
}
