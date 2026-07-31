package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.BonReception;
import com.hemodialyse.backend.domain.stock.model.BonStatut;
import com.hemodialyse.backend.domain.stock.model.LigneReception;
import com.hemodialyse.backend.domain.stock.port.BonReceptionRepositoryPort;
import com.hemodialyse.backend.infrastructure.persistence.entity.BonReceptionJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.LigneReceptionJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.BonReceptionJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.repository.LigneReceptionJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class BonReceptionRepositoryAdapter implements BonReceptionRepositoryPort {

    private final BonReceptionJpaRepository jpa;
    private final LigneReceptionJpaRepository ligneJpa;

    public BonReceptionRepositoryAdapter(BonReceptionJpaRepository jpa, LigneReceptionJpaRepository ligneJpa) {
        this.jpa = jpa;
        this.ligneJpa = ligneJpa;
    }

    @Override
    @Transactional
    public BonReception save(BonReception bon) {
        jpa.save(toJpa(bon));
        ligneJpa.deleteByBonReceptionId(bon.getId());
        for (LigneReception l : bon.getLignes()) {
            ligneJpa.save(toJpa(bon.getId(), l));
        }
        return findById(bon.getId(), CenterId.of(bon.getCenterId())).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BonReception> findById(UUID id, CenterId centerId) {
        return jpa.findByIdAndCenterId(id, centerId.value()).map(this::toDomainWithLines);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BonReception> findAll(CenterId centerId) {
        return jpa.findByCenterIdOrderByCreatedAtDesc(centerId.value())
                .stream().map(this::toDomainWithLines).toList();
    }

    private BonReceptionJpaEntity toJpa(BonReception b) {
        BonReceptionJpaEntity e = new BonReceptionJpaEntity();
        e.setId(b.getId());
        e.setCenterId(b.getCenterId());
        e.setReference(b.getReference());
        e.setBonCommandeId(b.getBonCommandeId());
        e.setFournisseurId(b.getFournisseurId());
        e.setDateReception(b.getDateReception());
        e.setStatut(b.getStatut().name());
        e.setCreatedBy(b.getCreatedBy());
        e.setCreatedAt(b.getCreatedAt());
        return e;
    }

    private LigneReceptionJpaEntity toJpa(UUID bonId, LigneReception l) {
        LigneReceptionJpaEntity e = new LigneReceptionJpaEntity();
        e.setId(l.id() != null ? l.id() : UUID.randomUUID());
        e.setBonReceptionId(bonId);
        e.setArticleId(l.articleId());
        e.setQuantite(l.quantite());
        e.setPrixUnitaire(l.prixUnitaire());
        e.setNumeroLot(l.numeroLot());
        e.setDatePeremption(l.datePeremption());
        e.setEmplacementId(l.emplacementId());
        e.setLotId(l.lotId());
        return e;
    }

    private BonReception toDomainWithLines(BonReceptionJpaEntity e) {
        BonReception b = new BonReception();
        b.setId(e.getId());
        b.setCenterId(e.getCenterId());
        b.setReference(e.getReference());
        b.setBonCommandeId(e.getBonCommandeId());
        b.setFournisseurId(e.getFournisseurId());
        b.setDateReception(e.getDateReception());
        b.setStatut(BonStatut.valueOf(e.getStatut()));
        b.setCreatedBy(e.getCreatedBy());
        b.setCreatedAt(e.getCreatedAt());
        b.remplacerLignes(ligneJpa.findByBonReceptionId(e.getId()).stream()
                .map(l -> new LigneReception(l.getId(), l.getArticleId(), l.getQuantite(), l.getPrixUnitaire(),
                        l.getNumeroLot(), l.getDatePeremption(), l.getEmplacementId(), l.getLotId()))
                .toList());
        return b;
    }
}

