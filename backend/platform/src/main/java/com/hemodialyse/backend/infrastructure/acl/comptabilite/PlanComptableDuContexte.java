package com.hemodialyse.backend.infrastructure.acl.comptabilite;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.PlanComptableEnLecture;
import com.hemodialyse.backend.domain.comptabilite.port.PlanComptableUseCase;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

/**
 * ACL temporaire (retirée avec les slices C2 à C7) : le plan comptable est tenu par le contexte {@code comptabilite} (C1) ;
 * les services legacy du mapping et des pièces le lisent par le langage publié, avec leur erreur
 * {@code COMPTE_INCONNU}.
 */
@Component
public class PlanComptableDuContexte implements PlanComptableUseCase {

    private final PlanComptableEnLecture plan;

    public PlanComptableDuContexte(PlanComptableEnLecture plan) {
        this.plan = plan;
    }

    @Override
    public void exigerActifs(UUID centerId, Collection<String> numeros) {
        for (String numero : numeros) {
            if (numero == null || numero.isBlank()) continue;
            String compte = numero.trim();
            // Le legacy comparait la casse exacte et conserve le numéro saisi tel quel (mapping, pièces) : un numéro en
            // minuscules reste refusé alors que le plan est désormais normalisé en majuscules (KB-CPT-04, reproduit ici).
            if (!compte.equals(compte.toUpperCase()) || !plan.estUnCompteActif(centerId, compte)) {
                throw new BusinessException("COMPTE_INCONNU", "Le compte " + compte
                        + " n'existe pas dans le plan comptable du centre ou est désactivé");
            }
        }
    }
}
