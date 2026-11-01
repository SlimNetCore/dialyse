package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.SeanceRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;

/**
 * Handler « Verrouillage facturation » : sur réception de {@code FacturationValidée}, pose le verrou d'immuabilité
 * sur les séances de la période (RG-SEA-002, ADR 0002). Idempotent : une seconde réception ne change rien.
 */
public class VerrouillageFacturation {

    private final SeanceRepository seances;

    public VerrouillageFacturation(SeanceRepository seances) {
        this.seances = seances;
    }

    public void executer(CentreId centre, LocalDate debut, LocalDate fin) {
        if (centre == null || debut == null || fin == null) return;
        seances.verrouillerPourFacturation(centre, debut, fin);
    }
}