package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Port sortant : le temps. « Aujourd'hui » est le jour du centre, jugé à son fuseau (clé
 * {@code app.planification.fuseau-par-defaut}, Africa/Algiers par défaut), partout, {@code affecterAssure} compris (décision du
 * 02/11, KB-PAT-17).
 */
public interface HorlogeDesCentres {

    LocalDate aujourdhui(CentreId centre);

    Instant maintenant();
}
