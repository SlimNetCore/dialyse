package com.hemodialyse.backend.domain.referential.port;

import com.hemodialyse.backend.domain.referential.port.ReferentialRepositoryPort.CentrePayeurDetail;
import com.hemodialyse.backend.domain.referential.port.ReferentialRepositoryPort.RefItem;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.UUID;

/**
 * Port In — Referential use cases (domain-level).
 */
public interface ReferentialUseCase {
    List<RefItem> centresPayeurs(CenterId c);
    List<RefItem> agences(CenterId c);
    List<RefItem> caisses(CenterId c);
    List<RefItem> medecins(CenterId c);
    List<RefItem> salles(CenterId c);
    List<RefItem> positions(CenterId c);
    List<RefItem> transporteurs(CenterId c);
    List<RefItem> categoriesTransport(CenterId c);
    List<RefItem> forfaits(CenterId c);
    List<RefItem> articles(CenterId c);

    List<RefItem> generateurs(CenterId c);

    List<RefItem> generateursBySalle(CenterId c, UUID salleId);

    List<CentrePayeurDetail> centresPayeursDetails(CenterId c);
}

