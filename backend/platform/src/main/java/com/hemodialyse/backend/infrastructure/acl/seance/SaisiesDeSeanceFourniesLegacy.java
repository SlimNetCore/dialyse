package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.SaisiesDeSeanceFournies;
import com.hemodialyse.backend.domain.medical.anemie.port.AdministrationTraitementRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fournit à la Séance le langage publié {@link SaisiesDeSeanceFournies} (SE6 ; réduit par SE7f, ADR 0100) : efface les administrations de
 * traitement (EPO, fer : table du Dossier médical, encore tenue par le port legacy {@code bc-medical}) d'une séance supprimée. Les deux
 * volets sont effacés par la Séance elle-même depuis SE7f. Aucune transaction propre : l'effacement suit l'unité de travail de la
 * suppression (tout ou rien).
 */
@Component
public class SaisiesDeSeanceFourniesLegacy implements SaisiesDeSeanceFournies {

    private final AdministrationTraitementRepositoryPort administrations;

    public SaisiesDeSeanceFourniesLegacy(AdministrationTraitementRepositoryPort administrations) {
        this.administrations = administrations;
    }

    @Override
    public void effacerLesAdministrations(UUID centre, UUID seance) {
        administrations.deleteBySeanceId(seance, CenterId.of(centre));
    }
}
