package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.BonCommande;
import com.hemodialyse.backend.domain.stock.model.BonStatut;
import com.hemodialyse.backend.domain.stock.model.LigneBonCommande;
import com.hemodialyse.backend.domain.stock.port.BonCommandeRepositoryPort;
import com.hemodialyse.backend.infrastructure.persistence.entity.BonCommandeJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.LigneBonCommandeJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.BonCommandeJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.repository.LigneBonCommandeJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class BonCommandeRepositoryAdapter implements BonCommandeRepositoryPort {

    private final BonCommandeJpaRepository jpa;
    private final LigneBonCommandeJpaRepository ligneJpa;

    public BonCommandeRepositoryAdapter(BonCommandeJpaRepository jpa, LigneBonCommandeJpaRepository ligneJpa) {
        this.jpa = jpa;
        this.ligneJpa = ligneJpa;
    }

    @Override
    @Transactional
    public BonCommande save(BonCommande bon) {
        jpa.save(toJpa(bon));
        ligneJpa.deleteByBonCommandeId(bon.getId());
        for (LigneBonCommande l : bon.getLignes()) {
            ligneJpa.save(toJpa(bon.getId(), l));
        }
        return get(bon.getId(), bon.getCenterId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BonCommande> findById(UUID id, CenterId centerId) {
        return jpa.findByIdAndCenterId(id, centerId.value()).map(this::toDomainWithLines);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BonCommande> findAll(CenterId centerId) {
        return jpa.findByCenterIdOrderByCreatedAtDesc(centerId.value())
                .stream().map(this::toDomainWithLines).toList();
    }

    private BonCommande get(UUID id, UUID centerId) {
        return findById(id, CenterId.of(centerId)).orElseThrow();
    }

    private BonCommandeJpaEntity toJpa(BonCommande b) {
        BonCommandeJpaEntity e = new BonCommandeJpaEntity();
        e.setId(b.getId());
        e.setCenterId(b.getCenterId());
        e.setReference(b.getReference());
        e.setFournisseurId(b.getFournisseurId());
        e.setStatut(b.getStatut().name());
        e.setCreatedBy(b.getCreatedBy());
        e.setCreatedAt(b.getCreatedAt());
        return e;
    }

    private LigneBonCommandeJpaEntity toJpa(UUID bonId, LigneBonCommande l) {
        LigneBonCommandeJpaEntity e = new LigneBonCommandeJpaEntity();
        e.setId(l.id() != null ? l.id() : UUID.randomUUID());
        e.setBonCommandeId(bonId);
        e.setArticleId(l.articleId());
        e.setQuantite(l.quantite());
        e.setPrixUnitaire(l.prixUnitaire());
        return e;
    }

    private BonCommande toDomainWithLines(BonCommandeJpaEntity e) {
        BonCommande b = new BonCommande();
        b.setId(e.getId());
        b.setCenterId(e.getCenterId());
        b.setReference(e.getReference());
        b.setFournisseurId(e.getFournisseurId());
        b.setStatut(BonStatut.valueOf(e.getStatut()));
        b.setCreatedBy(e.getCreatedBy());
        b.setCreatedAt(e.getCreatedAt());
        b.remplacerLignes(ligneJpa.findByBonCommandeId(e.getId()).stream()
                .map(l -> new LigneBonCommande(l.getId(), l.getArticleId(), l.getQuantite(), l.getPrixUnitaire()))
                .toList());
        return b;
    }
}

