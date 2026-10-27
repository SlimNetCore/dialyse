package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre;

import java.util.List;
import java.util.UUID;

/** Adapter JPA du langage publié {@link GenerateursDuCentre} : lecture de {@code gmao_equipements}. */
public class GenerateursDuCentreJpa implements GenerateursDuCentre {

    private final EquipementSpringData equipements;

    public GenerateursDuCentreJpa(EquipementSpringData equipements) {
        this.equipements = equipements;
    }

    @Override
    public List<Generateur> duCentre(UUID centreId) {
        return equipements.generateursDuCentre(centreId).stream()
                .map(l -> new Generateur((UUID) l[0], (String) l[1], (String) l[2], (UUID) l[3], (String) l[4], centreId))
                .toList();
    }
}
