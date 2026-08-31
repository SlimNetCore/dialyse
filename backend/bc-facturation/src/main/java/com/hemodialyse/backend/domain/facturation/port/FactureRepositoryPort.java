package com.hemodialyse.backend.domain.facturation.port;

import com.hemodialyse.backend.domain.facturation.aggregate.FactureAggregate;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;

public interface FactureRepositoryPort {
    void saveAll(List<FactureAggregate> factures);

    int currentInvoiceSequence(CenterId centerId, LocalDate billingDate);

    String nextInvoiceNumber(CenterId centerId, String codeFormat, LocalDate billingDate);
}

