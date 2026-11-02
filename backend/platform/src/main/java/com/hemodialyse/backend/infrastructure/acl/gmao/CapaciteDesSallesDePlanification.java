package com.hemodialyse.backend.infrastructure.acl.gmao;

import com.hemodialyse.backend.contexts.gmao.application.equipement.CapaciteDesSalles;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.SalleId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * ACL du contexte GMAO vers la capacité des salles, fournie par la Planification (port publié, P9, RG-PLN-061) : traduit le refus de
 * la Planification en refus 422 {@code SALLE_CAPACITE_DEPASSEE}, avec le même message qu'avant la migration.
 */
@Component
public class CapaciteDesSallesDePlanification implements CapaciteDesSalles {

    private final com.hemodialyse.backend.contexts.planification.publishedlanguage.CapaciteDesSalles planification;

    public CapaciteDesSallesDePlanification(
            com.hemodialyse.backend.contexts.planification.publishedlanguage.CapaciteDesSalles planification) {
        this.planification = planification;
    }

    @Override
    public void verifierAffectation(CentreId centre, SalleId salle, EquipementId equipement) {
        try {
            planification.verifierAffectation(centre.valeur(), salle == null ? null : salle.valeur(),
                    equipement == null ? null : equipement.valeur());
        } catch (com.hemodialyse.backend.contexts.planification.publishedlanguage.CapaciteDesSalles.CapaciteDeLaSalleDepassee refus) {
            throw new BusinessException("SALLE_CAPACITE_DEPASSEE", refus.getMessage());
        }
    }
}
