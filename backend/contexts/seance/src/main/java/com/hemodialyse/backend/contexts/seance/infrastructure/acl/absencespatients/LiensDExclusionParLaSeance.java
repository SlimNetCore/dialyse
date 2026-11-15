package com.hemodialyse.backend.contexts.seance.infrastructure.acl.absencespatients;

import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.LiensDExclusionFournis;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.LiensDExclusionPublies;

import java.util.UUID;

/**
 * Fournisseur des Absences de patients (ADR 0098, SE7d) : le lien d'exclusion passe par {@link LiensDExclusionPublies} (sans statut,
 * KB-SEA-81). Logé dans la Séance (ADR 0192) : la Séance dépend des Absences, jamais l'inverse. Délégation pure, lecture seule.
 */
public class LiensDExclusionParLaSeance implements LiensDExclusionFournis {

    private final LiensDExclusionPublies liens;

    public LiensDExclusionParLaSeance(LiensDExclusionPublies liens) {
        this.liens = liens;
    }

    @Override
    public boolean absenceLiee(UUID centre, UUID absence) {
        return liens.absenceLiee(centre, absence);
    }
}
