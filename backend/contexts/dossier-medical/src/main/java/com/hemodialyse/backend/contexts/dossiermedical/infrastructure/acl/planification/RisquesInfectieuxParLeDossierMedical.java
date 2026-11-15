package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.RisquesInfectieuxPublies;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.RisquesInfectieuxFournis;
import java.util.Set;
import java.util.UUID;

/**
 * Traducteur (DM9, ADR 0101) : fournit à la Planification {@link RisquesInfectieuxFournis} (planification.v1) depuis le langage publié du
 * Dossier médical {@link RisquesInfectieuxPublies} (dossier-medical.v1). La Planification ne peut pas dépendre du module du Dossier médical
 * (cycle Maven : celui-ci dépend déjà d'elle) ; le Dossier médical héberge donc lui-même le traducteur (ADR 0190, port fourni).
 */
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
