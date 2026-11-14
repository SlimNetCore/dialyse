package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.DescriptionsDesEquipements;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Double en mémoire du langage publié de la GMAO ({@link DescriptionsDesEquipements}) : borné au centre, équipements supprimés
 * décrits (comme l'adapter réel, dont le contrat partagé est dans le contexte GMAO : {@code PilotageContrat}).
 */
public class EquipementsEnMemoire implements DescriptionsDesEquipements {

    private final Map<UUID, UUID> centreDe = new HashMap<>();
    private final Map<UUID, DescriptionDeLEquipement> descriptions = new HashMap<>();

    public EquipementsEnMemoire equipement(UUID centre, UUID id, String code, String fabricant, String statut) {
        centreDe.put(id, centre);
        descriptions.put(id, new DescriptionDeLEquipement(id, code, fabricant, statut));
        return this;
    }

    @Override
    public Map<UUID, DescriptionDeLEquipement> duCentre(UUID centre, Collection<UUID> equipements) {
        Map<UUID, DescriptionDeLEquipement> lues = new HashMap<>();
        for (UUID id : equipements) {
            if (id != null && centre.equals(centreDe.get(id))) lues.put(id, descriptions.get(id));
        }
        return lues;
    }
}
