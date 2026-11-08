package com.hemodialyse.backend.infrastructure.acl.absences;

import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.LiensDExclusionFournis;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.LiensDExclusionPublies;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fournisseur des Absences de patients (ADR 0098, SE7d) : le lien d'exclusion passe par {@link LiensDExclusionPublies} (sans statut,
 * KB-SEA-81). Logé dans la plateforme (cycle Maven). Délégation pure, lecture seule.
 */
@Component
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
