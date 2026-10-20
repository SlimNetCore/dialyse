package com.hemodialyse.backend.domain.facturation.port;

public interface FacturationUseCase {
    FacturationDashboardResult dashboard(FacturationDashboardQuery query);
}
