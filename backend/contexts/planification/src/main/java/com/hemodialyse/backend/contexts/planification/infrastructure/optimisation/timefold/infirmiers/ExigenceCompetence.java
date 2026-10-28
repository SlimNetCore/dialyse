package com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold.infirmiers;

import com.hemodialyse.backend.contexts.planification.domain.Competence;
import com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold.CleCase;

/**
 * Fait du problème : une case où dialyse un patient qui demande une compétence particulière (pédiatrie, cathéter) ; au
 * moins un infirmier de la case doit l'avoir.
 */
public record ExigenceCompetence(CleCase cle, Competence competence) {
}
