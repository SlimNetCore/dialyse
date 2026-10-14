package com.hemodialyse.backend.contexts.gmao.application.planmaintenance;

import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

/**
 * L'équipement du plan à établir n'existe pas dans le référentiel. Refus explicite (écart de parité accepté, KB-GMAO-14 /
 * E-PG-1, décision du 2026-10-10) : le legacy répondait 500 par violation de clé étrangère. Étend
 * {@link IllegalArgumentException} comme {@link PlanIntrouvable} : l'interface répond 400 avec le code stable
 * {@code EQUIPEMENT_INCONNU}.
 */
public class EquipementInconnu extends IllegalArgumentException {

    public static final String CODE = "EQUIPEMENT_INCONNU";
    private final EquipementId equipement;

    public EquipementInconnu(EquipementId equipement) {
        super("Équipement non trouvé");
        this.equipement = equipement;
    }

    public EquipementId equipement() {
        return equipement;
    }
}
