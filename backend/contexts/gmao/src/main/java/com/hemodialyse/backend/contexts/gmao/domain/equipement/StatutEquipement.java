package com.hemodialyse.backend.contexts.gmao.domain.equipement;

/**
 * Statut d'un équipement (glossaire gmao ; RG-GMA-012). Les codes sont ceux du contrat observé et de la base.
 * {@code DESACTIF} et {@code A_REFORMER} n'ont aucune commande dédiée (KB-GMAO-06) : ils ne se posent que par état constaté
 * ou restent lisibles en base.
 */
public enum StatutEquipement {
    EN_SERVICE,
    EN_MAINTENANCE,
    EN_ATTENTE_PIECE,
    HORS_SERVICE,
    DESACTIF,
    A_REFORMER,
    REFORME;

    /**
     * Un patient peut être affecté à l'équipement (RG-GMA-066) : seulement s'il est en service. Un équipement désactivé n'est
     * plus disponible (KB-GMAO-73, corrigé : le legacy le disait disponible).
     */
    public boolean disponiblePourUnPatient() {
        return this == EN_SERVICE;
    }
}
