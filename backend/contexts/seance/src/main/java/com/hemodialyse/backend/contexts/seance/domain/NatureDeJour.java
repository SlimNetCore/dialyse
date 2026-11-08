package com.hemodialyse.backend.contexts.seance.domain;

/**
 * Nature d'un jour où le centre ne dialyse pas : jour férié (table {@code center_holiday}, colonne {@code label}) ou fermeture
 * exceptionnelle (table {@code center_closure_day}, colonne {@code reason}). Deux tables, deux routes, jamais mélangées (KB-SEA-58 :
 * supprimer un jour férié par la route des fermetures n'efface rien).
 */
public enum NatureDeJour {
    JOUR_FERIE, FERMETURE_EXCEPTIONNELLE
}
