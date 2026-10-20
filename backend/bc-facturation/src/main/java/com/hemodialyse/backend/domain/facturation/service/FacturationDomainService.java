package com.hemodialyse.backend.domain.facturation.service;

import com.hemodialyse.backend.domain.facturation.port.FacturationDashboardQuery;
import com.hemodialyse.backend.domain.facturation.port.FacturationDashboardResult;
import com.hemodialyse.backend.domain.facturation.port.FacturationUseCase;
import com.hemodialyse.backend.domain.facturation.port.SeanceFacturationPort;

/**
 * Reste du service legacy de facturation : le tableau de bord (slice F5). La simulation (F2), la validation et l'émission (F3) et
 * les paramètres (F1) sont servis par le contexte {@code facturation}.
 */
public class FacturationDomainService implements FacturationUseCase {

    private final SeanceFacturationPort seancePort;

    public FacturationDomainService(SeanceFacturationPort seancePort) {
        this.seancePort = seancePort;
    }

    @Override
    public FacturationDashboardResult dashboard(FacturationDashboardQuery query) {
        if (query.month() == null) {
            throw new IllegalArgumentException("Le mois est obligatoire");
        }
        return seancePort.loadDashboard(query.centerId(), query.month());
    }
}
