package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Administrations;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SaisiesDeSeanceFournies;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/**
 * Fournit à la Séance le langage publié {@link SaisiesDeSeanceFournies} (SE6 ; DM9, ADR 0101, remplace {@code SaisiesDeSeanceFourniesLegacy}
 * de la plateforme et le port {@code bc-medical}) : efface les administrations de traitement (EPO, fer) d'une séance supprimée. Aucune
 * transaction propre : l'effacement suit l'unité de travail de la suppression (tout ou rien).
 */
public class SaisiesDeSeanceParLeDossierMedical implements SaisiesDeSeanceFournies {

    private final Administrations administrations;

    public SaisiesDeSeanceParLeDossierMedical(Administrations administrations) {
        this.administrations = administrations;
    }

    @Override
    public void effacerLesAdministrations(UUID centre, UUID seance) {
        administrations.effacerCellesDeLaSeance(new CentreId(centre), seance);
    }
}
