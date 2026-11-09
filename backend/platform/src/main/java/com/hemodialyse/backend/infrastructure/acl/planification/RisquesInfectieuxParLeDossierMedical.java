package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.RisquesInfectieuxPublies;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.RisquesInfectieuxFournis;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Traducteur (DM9, ADR 0101) : fournit à la Planification {@link RisquesInfectieuxFournis} (planification.v1) depuis le langage publié du
 * Dossier médical {@link RisquesInfectieuxPublies} (dossier-medical.v1). La Planification ne peut pas dépendre du module du Dossier médical
 * (cycle Maven : celui-ci dépend déjà d'elle) ; la plateforme fait le pont, comme pour les lectures de la Séance (SE7c, SE7d).
 */
@Component
public class RisquesInfectieuxParLeDossierMedical implements RisquesInfectieuxFournis {

    private final RisquesInfectieuxPublies risques;

    public RisquesInfectieuxParLeDossierMedical(RisquesInfectieuxPublies risques) {
        this.risques = risques;
    }

    @Override
    public Set<UUID> patientsARisque(UUID centre) {
        return risques.patientsARisque(centre);
    }
}
