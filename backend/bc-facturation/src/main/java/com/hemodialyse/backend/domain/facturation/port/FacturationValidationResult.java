package com.hemodialyse.backend.domain.facturation.port;

public record FacturationValidationResult(int createdInvoices, int billedSeances) {
}

