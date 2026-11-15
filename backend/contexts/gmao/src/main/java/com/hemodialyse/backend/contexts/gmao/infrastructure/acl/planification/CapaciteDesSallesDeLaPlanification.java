package com.hemodialyse.backend.contexts.gmao.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.gmao.application.equipement.CapaciteDesSalles;
import com.hemodialyse.backend.contexts.gmao.application.equipement.SalleADCapaciteMaximale;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.SalleId;
import com.hemodialyse.backend.contexts.gmao.publishedlanguage.CapaciteDesSallesFournie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * ACL de la GMAO vers la capacité des salles, fournie par la Planification (port fourni, P9, RG-PLN-061, ADR 0192) : traduit le refus de
 * la Planification en refus de la GMAO {@link SalleADCapaciteMaximale} (422 {@code SALLE_CAPACITE_DEPASSEE}, même message qu'avant).
 */
public class CapaciteDesSallesDeLaPlanification implements CapaciteDesSalles {

    private final CapaciteDesSallesFournie planification;

    public CapaciteDesSallesDeLaPlanification(CapaciteDesSallesFournie planification) {
        this.planification = planification;
    }

    @Override
    public void verifierAffectation(CentreId centre, SalleId salle, EquipementId equipement) {
        try {
            planification.verifierAffectation(centre.valeur(), salle == null ? null : salle.valeur(),
                    equipement == null ? null : equipement.valeur());
        } catch (CapaciteDesSallesFournie.CapaciteDeLaSalleAtteinte refus) {
            throw new SalleADCapaciteMaximale(refus.getMessage());
        }
    }
}
