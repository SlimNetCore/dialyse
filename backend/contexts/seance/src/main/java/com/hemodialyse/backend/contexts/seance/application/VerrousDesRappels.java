package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;

/**
 * Port sortant : sérialise les rappels d'un même centre pour un même jour du centre, jusqu'à la fin de l'unité de travail (deux
 * instances du serveur ne rappellent pas deux fois, KB-SEA-35). Verrou non obtenu dans le délai : {@code EcritureConcurrente}. Sans
 * effet hors PostgreSQL.
 */
public interface VerrousDesRappels {
    void verrouiller(CentreId centre, LocalDate jour);
}
