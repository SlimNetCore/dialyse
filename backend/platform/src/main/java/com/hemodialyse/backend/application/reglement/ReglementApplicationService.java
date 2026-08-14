package com.hemodialyse.backend.application.reglement;

import com.hemodialyse.backend.domain.reglement.port.ReglementUseCase;
import com.hemodialyse.backend.domain.reglement.repository.ReglementRepository;
import com.hemodialyse.backend.domain.reglement.service.ReglementService;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ReglementApplicationService implements ReglementUseCase {

    private final ReglementService delegate;

    public ReglementApplicationService(ReglementRepository repository) {
        this.delegate = new ReglementService(repository);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<ReglementFactureListItem> search(ReglementSearchQuery query) {
        return delegate.search(query);
    }

    @Override
    @Transactional(readOnly = true)
    public ReglementDashboardResult dashboard(ReglementDashboardQuery query) {
        return delegate.dashboard(query);
    }

    @Override
    public ReglementFactureListItem registerPayment(RegisterFacturePaymentCommand command) {
        return delegate.registerPayment(command);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FacturePaymentItem> getPaymentHistory(GetPaymentHistoryQuery query) {
        return delegate.getPaymentHistory(query);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReglementFactureListItem> exportList(ReglementSearchQuery query) {
        return delegate.exportList(query);
    }
}


