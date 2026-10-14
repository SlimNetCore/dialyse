package com.hemodialyse.backend.contexts.gmao.domain.intervention;

/** Type d'une intervention de maintenance (RG-GMA-020). Les codes sont ceux du contrat observé et de la base. */
public enum TypeIntervention {
    PREVENTIVE,
    CURATIVE,
    URGENTE,
    CONTROLE,
    INSTALLATION,
    DEINSTALLATION,
    REMPLACEMENT_PIECE,
    REVISION_COMPLETE
}
