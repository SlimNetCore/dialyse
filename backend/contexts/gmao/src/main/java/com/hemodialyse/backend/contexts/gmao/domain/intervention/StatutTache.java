package com.hemodialyse.backend.contexts.gmao.domain.intervention;

/** Avancement d'une tâche d'intervention : {@code A_FAIRE -> EN_COURS -> COMPLETEE}, sans retour (à valider par le métier). */
public enum StatutTache {
    A_FAIRE, EN_COURS, COMPLETEE
}
