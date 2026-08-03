package com.hemodialyse.backend.domain.facturation.port;

import com.hemodialyse.backend.domain.facturation.valueobject.ParametresFacturation;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

public interface FacturationSettingsRepositoryPort {
    ParametresFacturation findByCenterId(CenterId centerId);

    ParametresFacturation save(CenterId centerId, String userId, ParametresFacturation settings);
}

