package com.hemodialyse.backend.application.facturation;

import com.hemodialyse.backend.domain.facturation.port.FacturationDashboardQuery;
import com.hemodialyse.backend.domain.facturation.port.FacturationDashboardResult;
import com.hemodialyse.backend.domain.facturation.port.FacturationUseCase;
import com.hemodialyse.backend.domain.facturation.port.SeanceFacturationPort;
import com.hemodialyse.backend.domain.facturation.service.FacturationDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reste du service d'application legacy : le tableau de bord de facturation (slice F5). La validation de facturation (F3) est
 * servie par le contexte {@code facturation} ({@code ValiderLaFacturation}).
 */
@Service
@Transactional
public class FacturationApplicationService implements FacturationUseCase {

    private final FacturationDomainService delegate;

    public FacturationApplicationService(SeanceFacturationPort seancePort) {
        this.delegate = new FacturationDomainService(seancePort);
    }

    @Override
    @Transactional(readOnly = true)
    public FacturationDashboardResult dashboard(FacturationDashboardQuery query) {
        return delegate.dashboard(query);
    }
}
