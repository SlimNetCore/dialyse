package com.hemodialyse.backend.contexts.planification.infrastructure.acl.gmao;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.CapaciteDesSallesFournie;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CapaciteDesSalles;

import java.util.UUID;

/**
 * Fournit à la GMAO le port {@link CapaciteDesSallesFournie} (P9, RG-PLN-061, ADR 0192) à partir du langage publié de la Planification
 * {@link CapaciteDesSalles} : le refus de la Planification devient le refus du port fourni, avec le même message. Hébergé par la
 * Planification, qui dépend de la GMAO (jamais l'inverse). Délégation pure, lecture seule.
 */
public class CapaciteDesSallesFournieParLaPlanification implements CapaciteDesSallesFournie {

    private final CapaciteDesSalles capacites;

    public CapaciteDesSallesFournieParLaPlanification(CapaciteDesSalles capacites) {
        this.capacites = capacites;
    }

    @Override
    public void verifierAffectation(UUID centre, UUID salle, UUID equipement) {
        try {
            capacites.verifierAffectation(centre, salle, equipement);
        } catch (CapaciteDesSalles.CapaciteDeLaSalleDepassee refus) {
            throw new CapaciteDeLaSalleAtteinte(refus.getMessage());
        }
    }
}
