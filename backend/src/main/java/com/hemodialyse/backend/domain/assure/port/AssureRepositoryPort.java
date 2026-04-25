package com.hemodialyse.backend.domain.assure.port;

import com.hemodialyse.backend.domain.assure.model.Assure;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.Optional;

public interface AssureRepositoryPort {
    Assure save(Assure assure);
    Optional<Assure> findByNumeroAssurance(String numeroAssurance);
    List<Assure> searchByCenter(CenterId centerId, String query);
}
