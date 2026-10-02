package com.hemodialyse.backend.domain.planning.port;

import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.DonneesSemaine;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port de lecture du planning d'une semaine (toujours borné au centre — AGENTS.md §2).
 */
public interface PlanningSemainePort {

    /**
     * @param debutSemaine dimanche de la semaine lue ; sert à relever les fermetures datées de la semaine
     */
    DonneesSemaine charger(UUID centerId, LocalDate debutSemaine);
}
