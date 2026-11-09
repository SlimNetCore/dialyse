package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.StockDuCentre;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port sortant : le stock de chaque centre ÉVALUÉ À LA DATE DONNÉE (la fin de la période : KB-DIR-07), lu dans le Stock. Un centre inconnu du
 * Stock est absent du résultat.
 */
public interface StockDesCentres {

    Map<UUID, StockDuCentre> aLaDate(List<UUID> centres, LocalDate date);
}
