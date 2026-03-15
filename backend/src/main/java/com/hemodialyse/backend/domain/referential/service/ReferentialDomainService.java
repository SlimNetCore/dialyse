package com.hemodialyse.backend.domain.referential.service;

import com.hemodialyse.backend.domain.referential.port.ReferentialUseCase;
import com.hemodialyse.backend.domain.referential.port.ReferentialRepositoryPort;
import com.hemodialyse.backend.domain.referential.port.ReferentialRepositoryPort.RefItem;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Domain Service — Referential data retrieval.
 */
@Service
public class ReferentialDomainService implements ReferentialUseCase {

    private final ReferentialRepositoryPort repo;

    public ReferentialDomainService(ReferentialRepositoryPort repo) { this.repo = repo; }

    @Override public List<RefItem> centresPayeurs(CenterId c) { return repo.findCentresPayeurs(c); }
    @Override public List<RefItem> agences(CenterId c) { return repo.findAgences(c); }
    @Override public List<RefItem> caisses(CenterId c) { return repo.findCaisses(c); }
    @Override public List<RefItem> medecins(CenterId c) { return repo.findMedecins(c); }
    @Override public List<RefItem> salles(CenterId c) { return repo.findSalles(c); }
    @Override public List<RefItem> positions(CenterId c) { return repo.findPositions(c); }
    @Override public List<RefItem> transporteurs(CenterId c) { return repo.findTransporteurs(c); }
    @Override public List<RefItem> categoriesTransport(CenterId c) { return repo.findCategoriesTransport(c); }
    @Override public List<RefItem> forfaits(CenterId c) { return repo.findForfaits(c); }
}

