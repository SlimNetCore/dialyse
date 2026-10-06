package com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers;

import com.hemodialyse.backend.domain.planning.optimisation.model.CompetenceInfirmier;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.CleCase;

/**
 * Fait du problème : une case où dialyse un patient qui demande une compétence particulière (pédiatrie, cathéter) ; au
 * moins un infirmier de la case doit l'avoir.
 */
public record ExigenceCompetence(CleCase cle, CompetenceInfirmier competence) {
}
