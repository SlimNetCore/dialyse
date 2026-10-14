package com.hemodialyse.backend.contexts.gmao.domain.equipement;

/**
 * R1 : la réforme est un état terminal, plus aucun changement de statut. Étend {@link IllegalStateException} : le contrat
 * observé est HTTP 422 BUSINESS_RULE_VIOLATION. Messages identiques au legacy (parité).
 */
public class EquipementReformeImmuable extends IllegalStateException {

    private EquipementReformeImmuable(String message) {
        super(message);
    }

    static EquipementReformeImmuable pourChangerDeStatut() {
        return new EquipementReformeImmuable("Un équipement réformé ne peut plus changer de statut");
    }

    static EquipementReformeImmuable pourRemettreEnService() {
        return new EquipementReformeImmuable("Un équipement réformé ne peut pas être réactivé");
    }
}
