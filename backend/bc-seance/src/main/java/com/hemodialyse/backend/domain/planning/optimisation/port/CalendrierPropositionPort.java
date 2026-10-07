package com.hemodialyse.backend.domain.planning.optimisation.port;

import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.CaseCalendrier;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port de persistance du planning calendaire des propositions d'optimisation (toujours borné au centre —
 * AGENTS.md §2). Le calendrier est figé à la fin du calcul : il ne dépend pas de l'état courant du centre.
 */
public interface CalendrierPropositionPort {

    /**
     * Enregistre (en remplaçant l'éventuel calendrier précédent) le calendrier d'une exécution.
     */
    void enregistrer(UUID centerId, UUID runId, List<CaseCalendrier> cases);

    /**
     * Lignes d'une semaine du calendrier, dans l'ordre créneau puis salle.
     */
    List<CaseCalendrier> lire(UUID centerId, UUID runId, LocalDate semaineDebut);

    /**
     * Semaines (dates de début, ordre croissant) disponibles pour l'exécution.
     */
    List<LocalDate> semaines(UUID centerId, UUID runId);

    /**
     * Supprime les calendriers des exécutions qui n'existent plus dans l'historique du centre.
     */
    void purgerOrphelins(UUID centerId);
}
