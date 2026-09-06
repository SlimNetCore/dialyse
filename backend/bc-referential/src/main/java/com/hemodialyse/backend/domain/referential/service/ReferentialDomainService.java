package com.hemodialyse.backend.domain.referential.service;

import com.hemodialyse.backend.domain.referential.port.ReferentialRepositoryPort;
import com.hemodialyse.backend.domain.referential.port.ReferentialRepositoryPort.CentrePayeurDetail;
import com.hemodialyse.backend.domain.referential.port.ReferentialRepositoryPort.RefItem;
import com.hemodialyse.backend.domain.referential.port.ReferentialUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.UUID;

/**
 * Domain Service — Referential data retrieval.
 * <p>
 * Pure domain class (no Spring/JPA dependency — hexagonal architecture, AGENTS.md §3).
 * Wired as a bean in {@code infrastructure/config/DomainServiceConfig}.
 */
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

    @Override
    public List<RefItem> etatsPatients(CenterId c) {
        return List.of(
                new RefItem("PERMANENT", "PERMANENT", "PERMANENT", null, null, null, null),
                new RefItem("OCCASIONNEL", "OCCASIONNEL", "OCCASIONNEL", null, null, null, null),
                new RefItem("VACANCIER_LOCAL", "VACANCIER_LOCAL", "VACANCIER_LOCAL", null, null, null, null),
                new RefItem("VACANCIER_ETRANGER", "VACANCIER_ETRANGER", "VACANCIER_ETRANGER", null, null, null, null),
                new RefItem("TRANSFERE", "TRANSFERE", "TRANSFERE", null, null, null, null),
                new RefItem("DECEDE", "DECEDE", "DECEDE", null, null, null, null),
                new RefItem("GREFFE", "GREFFE", "GREFFE", null, null, null, null),
                new RefItem("GUERRI", "GUERRI", "GUERRI", null, null, null, null)
        );
    }

    @Override
    public List<RefItem> articles(CenterId c) {
        return repo.findArticles(c);
    }

    @Override
    public List<RefItem> generateurs(CenterId c) {
        return repo.findGenerateurs(c);
    }

    @Override
    public List<RefItem> generateursBySalle(CenterId c, UUID salleId) {
        return repo.findGenerateursBySalle(c, salleId);
    }

    @Override
    public List<CentrePayeurDetail> centresPayeursDetails(CenterId c) {
        return repo.findCentresPayeursDetails(c);
    }
}

