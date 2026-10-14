package com.hemodialyse.backend.contexts.gmao.application.equipement;

/**
 * Équipement inconnu, ou d'un autre centre (indistinguable), sur une commande. Étend {@link IllegalArgumentException} pour
 * reproduire le contrat observé : HTTP 400 BAD_REQUEST « Équipement non trouvé » et non 404 (KB-GMAO-35).
 */
public class EquipementIntrouvable extends IllegalArgumentException {
    public EquipementIntrouvable() {
        super("Équipement non trouvé");
    }
}
