package com.hemodialyse.backend.application.planning;

import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.SemainePlanning;
import com.hemodialyse.backend.domain.planning.port.PlanningSemainePort;
import com.hemodialyse.backend.domain.planning.service.PlanningSemaineService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Planning réel d'une semaine d'un centre (qui dialyse où, jours fermés, conflits). Lecture seule, non mise en cache.
 */
@Service
public class PlanningSemaineQueryService {

    private final PlanningSemainePort port;

    public PlanningSemaineQueryService(PlanningSemainePort port) {
        this.port = port;
    }

    /**
     * @param date n'importe quel jour de la semaine voulue (semaine du dimanche au samedi)
     */
    public SemainePlanning semaine(UUID centerId, LocalDate date) {
        LocalDate debut = PlanningSemaineService.debutSemaine(date);
        return PlanningSemaineService.construire(port.charger(centerId, debut), debut);
    }
}
