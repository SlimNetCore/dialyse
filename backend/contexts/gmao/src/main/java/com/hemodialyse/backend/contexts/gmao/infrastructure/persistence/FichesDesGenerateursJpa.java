package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.FichesDesGenerateurs;

import java.util.Optional;
import java.util.UUID;

/** Adapter JPA du langage publié {@link FichesDesGenerateurs} : lecture de {@code gmao_equipements}. */
public class FichesDesGenerateursJpa implements FichesDesGenerateurs {

    private final EquipementSpringData equipements;

    public FichesDesGenerateursJpa(EquipementSpringData equipements) {
        this.equipements = equipements;
    }

    @Override
    public Optional<FicheDuGenerateur> duCentre(UUID centre, UUID equipement) {
        return equipements.ficheDuCentre(centre, equipement).stream()
                .map(l -> new FicheDuGenerateur((UUID) l[0], (String) l[1], (String) l[2], (String) l[3])).findFirst();
    }
}
