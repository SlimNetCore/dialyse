package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.diffusion;

import com.hemodialyse.backend.contexts.pilotagedirection.application.DiffusionDesChangements;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Changement;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.DiffusionDuTableauDeBordFournie;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.DiffusionDuTableauDeBordFournie.ChangementDuTableauDeBord;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.DiffusionDuTableauDeBordFournie.TableauDeBordModifie;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Adaptateur sortant : traduit les changements du domaine en message du canal fourni par la plateforme (STOMP). */
public final class DiffusionParLeCanalFourni implements DiffusionDesChangements {

    private final DiffusionDuTableauDeBordFournie canal;

    public DiffusionParLeCanalFourni(DiffusionDuTableauDeBordFournie canal) {
        this.canal = canal;
    }

    @Override
    public void diffuser(UUID societeId, Instant a, List<Changement> changements) {
        List<ChangementDuTableauDeBord> traduits = changements.stream()
                .map(c -> new ChangementDuTableauDeBord(c.centreId(), c.centre(), c.famille(), c.nom(), c.avant(), c.apres()))
                .toList();
        canal.publish(societeId, new TableauDeBordModifie(TableauDeBordModifie.TYPE, societeId, a, traduits));
    }
}
