package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.BonSortie;
import com.hemodialyse.backend.domain.stock.model.LigneSortie;
import com.hemodialyse.backend.domain.stock.port.BonSortieRepositoryPort;
import com.hemodialyse.backend.infrastructure.persistence.entity.BonSortieJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.LigneSortieJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.BonSortieJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.repository.LigneSortieJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class BonSortieRepositoryAdapter implements BonSortieRepositoryPort {

    private final BonSortieJpaRepository jpa;
    private final LigneSortieJpaRepository ligneJpa;

    public BonSortieRepositoryAdapter(BonSortieJpaRepository jpa, LigneSortieJpaRepository ligneJpa) {
        this.jpa = jpa;
        this.ligneJpa = ligneJpa;
    }

    @Override
    @Transactional
    public BonSortie save(BonSortie bon) {
        jpa.save(toJpa(bon));
        ligneJpa.deleteByBonSortieId(bon.getId());
        for (LigneSortie l : bon.getLignes()) {
            ligneJpa.save(toJpa(bon.getId(), l));
        }
        return findById(bon.getId(), CenterId.of(bon.getCenterId())).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BonSortie> findById(UUID id, CenterId centerId) {
        return jpa.findByIdAndCenterId(id, centerId.value()).map(this::toDomainWithLines);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BonSortie> findAll(CenterId centerId) {
        return jpa.findByCenterIdOrderByCreatedAtDesc(centerId.value())
                .stream().map(this::toDomainWithLines).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BonSortie> findBySeance(UUID seanceId, CenterId centerId) {
        return jpa.findByCenterIdAndSeanceId(centerId.value(), seanceId)
                .stream().map(this::toDomainWithLines).toList();
    }

    private BonSortieJpaEntity toJpa(BonSortie b) {
        BonSortieJpaEntity e = new BonSortieJpaEntity();
        e.setId(b.getId());
        e.setCenterId(b.getCenterId());
        e.setReference(b.getReference());
        e.setSeanceId(b.getSeanceId());
        e.setPatientId(b.getPatientId());
        e.setPoste(b.getPoste());
        e.setDateSortie(b.getDateSortie());
        e.setCreatedBy(b.getCreatedBy());
        e.setCreatedAt(b.getCreatedAt());
        return e;
    }

    private LigneSortieJpaEntity toJpa(UUID bonId, LigneSortie l) {
        LigneSortieJpaEntity e = new LigneSortieJpaEntity();
        e.setId(l.id() != null ? l.id() : UUID.randomUUID());
        e.setBonSortieId(bonId);
        e.setArticleId(l.articleId());
        e.setLotId(l.lotId());
        e.setQuantite(l.quantite());
        e.setPmpApplique(l.pmpApplique());
        return e;
    }

    private BonSortie toDomainWithLines(BonSortieJpaEntity e) {
        BonSortie b = new BonSortie();
        b.setId(e.getId());
        b.setCenterId(e.getCenterId());
        b.setReference(e.getReference());
        b.setSeanceId(e.getSeanceId());
        b.setPatientId(e.getPatientId());
        b.setPoste(e.getPoste());
        b.setDateSortie(e.getDateSortie());
        b.setCreatedBy(e.getCreatedBy());
        b.setCreatedAt(e.getCreatedAt());
        ligneJpa.findByBonSortieId(e.getId()).forEach(l ->
                b.ajouterLigne(new LigneSortie(l.getId(), l.getArticleId(), l.getLotId(),
                        l.getQuantite(), l.getPmpApplique())));
        return b;
    }
}

