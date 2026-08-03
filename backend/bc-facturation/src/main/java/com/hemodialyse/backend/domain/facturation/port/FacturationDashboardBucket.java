package com.hemodialyse.backend.domain.facturation.port;

public record FacturationDashboardBucket(String code, String label, long seancesCount, long patientsCount) {
}

