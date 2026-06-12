package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Fournisseur;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FournisseurRepositoryPort {
    Fournisseur save(Fournisseur fournisseur);

    Optional<Fournisseur> findById(UUID id, CenterId centerId);

    List<Fournisseur> findAllActive(CenterId centerId);

    List<Fournisseur> search(CenterId centerId, String query);
}

