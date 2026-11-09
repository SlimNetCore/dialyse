package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.publie;

import com.hemodialyse.backend.contexts.pilotagedirection.application.ConstituerLeRapportDeSynthese;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection;

import java.time.LocalDate;
import java.util.UUID;

/** Adaptateur entrant du langage publié {@link RapportDeSyntheseDeLaDirection} : délègue au cas d'usage (aucune règle ici). */
public final class RapportDeSynthesePublie implements RapportDeSyntheseDeLaDirection {

    private final ConstituerLeRapportDeSynthese rapport;

    public RapportDeSynthesePublie(ConstituerLeRapportDeSynthese rapport) {
        this.rapport = rapport;
    }

    @Override
    public Rapport enDirect(UUID societeId, LocalDate from, LocalDate to) {
        return rapport.enDirect(societeId, from, to);
    }

    @Override
    public Rapport constituer(SourceDuRapport source) {
        return rapport.constituer(source);
    }
}
