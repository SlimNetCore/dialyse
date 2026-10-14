package com.hemodialyse.backend.contexts.gmao.domain.intervention;

/** Une intervention porte au plus {@link Intervention#LIMITE_DE_TACHES} tâches (à valider par le métier). HTTP 422. */
public class LimiteDeTachesAtteinte extends IllegalStateException {
    LimiteDeTachesAtteinte() {
        super("Une intervention ne peut pas porter plus de " + Intervention.LIMITE_DE_TACHES + " tâches");
    }
}
