package com.hemodialyse.backend.infrastructure.acl.gmao;

import com.hemodialyse.backend.application.planning.SalleGenerateursService;
import com.hemodialyse.backend.contexts.gmao.application.equipement.CapaciteDesSalles;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.SalleId;
import org.springframework.stereotype.Component;

/**
 * ACL du contexte GMAO vers la capacité des salles (Planification, encore dans le legacy) : délègue au service existant,
 * dont le refus 422 {@code SALLE_CAPACITE_DEPASSEE} traverse tel quel. Retirée quand les salles auront leur contexte.
 */
@Component
public class CapaciteDesSallesLegacy implements CapaciteDesSalles {

    private final SalleGenerateursService salles;

    public CapaciteDesSallesLegacy(SalleGenerateursService salles) {
        this.salles = salles;
    }

    @Override
    public void verifierAffectation(CentreId centre, SalleId salle, EquipementId equipement) {
        salles.verifierAffectation(centre.valeur(), salle == null ? null : salle.valeur(),
                equipement == null ? null : equipement.valeur());
    }
}
