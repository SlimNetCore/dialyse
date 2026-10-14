package com.hemodialyse.backend.contexts.gmao.domain.intervention;

/**
 * Nature d'une ligne de coût d'intervention (glossaire : ligne de coût). Le nom de l'énumération est celui du contrat
 * observé : une nature inconnue répond 400 « No enum constant ...TypeLigneCout.X » (KB-GMAO-53).
 */
public enum TypeLigneCout {
    PIECE,
    MAIN_OEUVRE,
    INTERVENANT,
    AUTRE
}
