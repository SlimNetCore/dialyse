package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.SaisiesDeSeanceFournies;
import com.hemodialyse.backend.domain.medical.anemie.port.AdministrationTraitementRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletMedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fournit à la Séance le langage publié {@link SaisiesDeSeanceFournies} (SE6) : efface les administrations de traitement et les volets
 * legacy d'une séance supprimée, avec les mêmes ports que l'ancienne suppression. Aucune transaction propre : les effacements suivent
 * l'unité de travail de la suppression (tout ou rien).
 */
@Component
public class SaisiesDeSeanceFourniesLegacy implements SaisiesDeSeanceFournies {

    private final AdministrationTraitementRepositoryPort administrations;
    private final VoletParamedicalRepositoryPort voletsParamedicaux;
    private final VoletMedicalRepositoryPort voletsMedicaux;

    public SaisiesDeSeanceFourniesLegacy(AdministrationTraitementRepositoryPort administrations,
                                         VoletParamedicalRepositoryPort voletsParamedicaux, VoletMedicalRepositoryPort voletsMedicaux) {
        this.administrations = administrations;
        this.voletsParamedicaux = voletsParamedicaux;
        this.voletsMedicaux = voletsMedicaux;
    }

    @Override
    public void effacerLesAdministrations(UUID centre, UUID seance) {
        administrations.deleteBySeanceId(seance, CenterId.of(centre));
    }

    @Override
    public void effacerLeVoletParamedical(UUID centre, UUID seance) {
        voletsParamedicaux.deleteBySeanceId(seance, CenterId.of(centre));
    }

    @Override
    public void effacerLeVoletMedical(UUID centre, UUID seance) {
        voletsMedicaux.deleteBySeanceId(seance, CenterId.of(centre));
    }
}
