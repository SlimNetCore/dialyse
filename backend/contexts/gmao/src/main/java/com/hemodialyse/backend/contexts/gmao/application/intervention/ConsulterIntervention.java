package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;

import java.util.Optional;

/** Consulte une intervention du centre ; inconnue ou d'un autre centre : vide (404 sans corps, KB-GMAO-53). */
public final class ConsulterIntervention {

    private final InterventionRepository interventions;
    private final Horloge horloge;

    public ConsulterIntervention(InterventionRepository interventions, Horloge horloge) {
        this.interventions = interventions;
        this.horloge = horloge;
    }

    public Optional<InterventionVue> executer(InterventionId id, CentreId centre) {
        return interventions.trouverParId(id)
                .filter(i -> i.centreId().equals(centre))
                .map(i -> InterventionVue.depuis(i, horloge.maintenant()));
    }
}
