package com.hemodialyse.backend.contexts.seance.interfaces.evenements;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FacturationValideeEvent;
import com.hemodialyse.backend.contexts.seance.application.VerrouillageFacturation;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.modulith.events.ApplicationModuleListener;

import java.util.UUID;

/**
 * Adapter entrant : consomme {@code FacturationValideeEvent} (Modulith, après commit, au moins une fois) et verrouille
 * les séances de la période (RG-SEA-002). Idempotent côté repository (verrou déjà posé = no-op).
 */
public class VerrouillageFacturationEcouteur {

    private final VerrouillageFacturation verrouillage;

    public VerrouillageFacturationEcouteur(VerrouillageFacturation verrouillage) {
        this.verrouillage = verrouillage;
    }

    @ApplicationModuleListener
    void surFacturationValidee(FacturationValideeEvent evenement) {
        CentreId centre = new CentreId(evenement.centre() instanceof UUID u ? u : UUID.fromString(evenement.centre().toString()));
        verrouillage.executer(centre, evenement.debut(), evenement.fin());
    }
}