package com.hemodialyse.backend.domain.reglement.repository;

import com.hemodialyse.backend.domain.reglement.aggregate.FactureReglementAggregate;
import com.hemodialyse.backend.domain.reglement.entity.FacturePayment;
import com.hemodialyse.backend.domain.reglement.port.ReglementUseCase.ReglementDashboardQuery;
import com.hemodialyse.backend.domain.reglement.port.ReglementUseCase.ReglementDashboardResult;
import com.hemodialyse.backend.domain.reglement.port.ReglementUseCase.ReglementFactureListItem;
import com.hemodialyse.backend.domain.reglement.port.ReglementUseCase.ReglementSearchQuery;
import com.hemodialyse.backend.domain.reglement.port.ReglementUseCase.FacturePaymentItem;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.UUID;

public interface ReglementRepository {

    PagedResult<ReglementFactureListItem> search(ReglementSearchQuery query);

    ReglementDashboardResult dashboard(ReglementDashboardQuery query);

    FactureReglementAggregate loadAggregate(CenterId centerId, UUID factureId);

    void savePayment(FacturePayment payment);

    ReglementFactureListItem getFactureItem(CenterId centerId, UUID factureId);

    List<FacturePaymentItem> getPaymentHistory(CenterId centerId, UUID factureId);

    List<ReglementFactureListItem> exportList(ReglementSearchQuery query);
}


