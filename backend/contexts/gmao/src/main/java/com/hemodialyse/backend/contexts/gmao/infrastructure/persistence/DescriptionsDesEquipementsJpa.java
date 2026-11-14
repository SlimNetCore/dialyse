package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.DescriptionsDesEquipements;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Adapter JPA du langage publié {@link DescriptionsDesEquipements} : lecture par lot de {@code gmao_equipements}, supprimés compris. */
public class DescriptionsDesEquipementsJpa implements DescriptionsDesEquipements {

    private final EquipementSpringData equipements;

    public DescriptionsDesEquipementsJpa(EquipementSpringData equipements) {
        this.equipements = equipements;
    }

    @Override
    public Map<UUID, DescriptionDeLEquipement> duCentre(UUID centre, Collection<UUID> ids) {
        List<UUID> demandes = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<UUID, DescriptionDeLEquipement> descriptions = new HashMap<>();
        if (demandes.isEmpty()) return descriptions;
        for (Object[] l : equipements.descriptionsDuCentre(centre, demandes)) {
            UUID id = (UUID) l[0];
            descriptions.put(id, new DescriptionDeLEquipement(id, (String) l[1], (String) l[2], (String) l[3]));
        }
        return descriptions;
    }
}
