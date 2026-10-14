package com.hemodialyse.backend.contexts.gmao.application;

import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

/**
 * Port sortant : l'équipement est-il connu ? Le plan ne le référence que par identité (ADR 0014) ; la question est posée
 * avant d'établir un plan (KB-GMAO-14). Depuis G3, GMAO est propriétaire des équipements : le repository des équipements
 * répond. Un équipement supprimé logiquement est encore connu (comme la clé étrangère).
 */
public interface EquipementExiste {

    boolean existe(EquipementId equipement);
}
