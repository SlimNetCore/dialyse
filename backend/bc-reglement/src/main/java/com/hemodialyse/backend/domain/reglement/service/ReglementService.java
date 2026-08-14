package com.hemodialyse.backend.domain.reglement.service;

import com.hemodialyse.backend.domain.reglement.aggregate.FactureReglementAggregate;
import com.hemodialyse.backend.domain.reglement.repository.ReglementRepository;
import com.hemodialyse.backend.domain.reglement.port.ReglementUseCase;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.util.List;

public class ReglementService implements ReglementUseCase {

    private final ReglementRepository repository;

    public ReglementService(ReglementRepository repository) {
        this.repository = repository;
    }

    @Override
    public PagedResult<ReglementFactureListItem> search(ReglementSearchQuery query) {
        return repository.search(query);
    }

    @Override
    public ReglementDashboardResult dashboard(ReglementDashboardQuery query) {
        return repository.dashboard(query);
    }

    @Override
    public ReglementFactureListItem registerPayment(RegisterFacturePaymentCommand command) {
        FactureReglementAggregate aggregate = repository.loadAggregate(command.centerId(), command.factureId());
        repository.savePayment(aggregate.enregistrerPaiement(
                command.montant(),
                command.effectiveDate(),
                command.effectiveUserId()
        ));
        return repository.getFactureItem(command.centerId(), command.factureId());
    }

    @Override
    public List<FacturePaymentItem> getPaymentHistory(GetPaymentHistoryQuery query) {
        return repository.getPaymentHistory(query.centerId(), query.factureId());
    }

    @Override
    public List<ReglementFactureListItem> exportList(ReglementSearchQuery query) {
        return repository.exportList(query);
    }
}



