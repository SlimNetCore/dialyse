package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.ParametrageDeLaPlanification;
import com.hemodialyse.backend.domain.planning.optimisation.model.ReglagesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.port.ReglagesOptimisationPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * ACL du legacy vers les réglages de la Planification (slice P1, ADR 0053) : lecture des réglages et des centres en
 * replanification nocturne par le langage publié du contexte.
 */
@Component
public class ReglagesOptimisationDuContexte implements ReglagesOptimisationPort {

    private final ParametrageDeLaPlanification parametrage;

    public ReglagesOptimisationDuContexte(ParametrageDeLaPlanification parametrage) {
        this.parametrage = parametrage;
    }

    @Override
    public ReglagesOptimisation lire(UUID centerId) {
        ParametrageDeLaPlanification.Reglages r = parametrage.reglages(centerId);
        return new ReglagesOptimisation(r.replanificationNocturne(), r.heuresParVacation(), r.heuresHebdoTempsPlein(),
                r.reposHebdoMin());
    }

    @Override
    public List<UUID> centresEnReplanificationAuto() {
        return parametrage.centresEnReplanificationNocturne();
    }
}
