package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.gmao;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.DescriptionsDesEquipements;
import com.hemodialyse.backend.contexts.patientcouverture.application.GenerateursDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ACL de lecture du générateur affecté (LEC-PAT-GMAO, ADR 0181) : l'équipement de la GMAO du centre, décrit par le langage publié
 * {@link DescriptionsDesEquipements} (gmao.v1). Un équipement supprimé reste décrit (dernier code, fabricant et statut connus), comme le
 * faisait la lecture SQL d'avant ; un équipement d'un autre centre ou inconnu est absent. Plus aucun SQL sur la table des équipements.
 */
public class GenerateursDuCentreParLaGmao implements GenerateursDuCentre {

    private final DescriptionsDesEquipements equipements;

    public GenerateursDuCentreParLaGmao(DescriptionsDesEquipements equipements) {
        this.equipements = equipements;
    }

    @Override
    public Optional<Generateur> trouver(CentreId centre, UUID id) {
        return Optional.ofNullable(equipements.duCentre(centre.valeur(), List.of(id)).get(id))
                .map(d -> new Generateur(d.id(), d.code(), d.fabricant(), d.statut()));
    }
}
