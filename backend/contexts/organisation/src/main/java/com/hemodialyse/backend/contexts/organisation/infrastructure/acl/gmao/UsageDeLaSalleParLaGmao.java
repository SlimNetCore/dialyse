package com.hemodialyse.backend.contexts.organisation.infrastructure.acl.gmao;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.EquipementsDeLaSalle;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.UsageDeLaSalle;

import java.util.UUID;

/**
 * Ce que la GMAO retient d'une salle (PL11-4, D3, ADR 0160) : les équipements (dont les générateurs) installés dans la salle. La GMAO ne
 * dépend pas de l'Organisation ; c'est l'Organisation qui lit, par cette ACL, le langage publié {@link EquipementsDeLaSalle}. Toute
 * erreur technique est propagée : l'Organisation refuse alors la suppression (D10).
 */
public class UsageDeLaSalleParLaGmao implements UsageDeLaSalle {

    private final EquipementsDeLaSalle equipements;

    public UsageDeLaSalleParLaGmao(EquipementsDeLaSalle equipements) {
        this.equipements = equipements;
    }

    @Override
    public long nombreDeReferences(UUID salle) {
        return equipements.nombre(salle);
    }
}
