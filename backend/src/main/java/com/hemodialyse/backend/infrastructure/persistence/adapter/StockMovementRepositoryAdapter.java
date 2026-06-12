package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.StockMovement;
import com.hemodialyse.backend.domain.stock.model.StockMovementType;
import com.hemodialyse.backend.domain.stock.port.StockMovementRepositoryPort;
import com.hemodialyse.backend.infrastructure.persistence.entity.StockMovementJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.StockMovementJpaRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Component
public class StockMovementRepositoryAdapter implements StockMovementRepositoryPort {

    private final StockMovementJpaRepository jpa;

    public StockMovementRepositoryAdapter(StockMovementJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public StockMovement save(StockMovement movement) {
        return toDomain(jpa.save(toJpa(movement)));
    }

    @Override
    public List<StockMovement> findByArticleOrdered(CenterId centerId, UUID articleId) {
        return jpa.findByCenterIdAndArticleIdOrderByCreatedAtAscIdAsc(centerId.value(), articleId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<StockMovement> findByArticleBefore(CenterId centerId, UUID articleId, OffsetDateTime before) {
        return jpa.findByCenterIdAndArticleIdAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(
                        centerId.value(), articleId, before)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void updatePmpApres(UUID movementId, BigDecimal pmpApres) {
        jpa.findById(movementId).ifPresent(e -> {
            e.setPmpApres(pmpApres);
            jpa.save(e);
        });
    }

    @Override
    public Optional<StockMovement> findFirstEntreeByLot(CenterId centerId, UUID lotId) {
        return jpa.findFirstByCenterIdAndLotIdAndMouvementTypeOrderByCreatedAtAsc(
                centerId.value(), lotId, StockMovementType.ENTREE.name()).map(this::toDomain);
    }

    @Override
    public void applyRecalc(List<MovementRecalc> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }
        List<UUID> ids = updates.stream().map(MovementRecalc::movementId).toList();
        Map<UUID, StockMovementJpaEntity> byId = new HashMap<>();
        for (StockMovementJpaEntity e : jpa.findAllById(ids)) {
            byId.put(e.getId(), e);
        }
        List<StockMovementJpaEntity> toSave = new java.util.ArrayList<>(updates.size());
        for (MovementRecalc u : updates) {
            StockMovementJpaEntity e = byId.get(u.movementId());
            if (e == null) {
                continue;
            }
            e.setPmpApres(u.pmpApres());
            if (u.valorisation() != null) {
                e.setPrixUnitaire(u.valorisation());
            }
            toSave.add(e);
        }
        jpa.saveAll(toSave);
    }

    private StockMovementJpaEntity toJpa(StockMovement m) {
        StockMovementJpaEntity entity = new StockMovementJpaEntity();
        entity.setId(m.getId());
        entity.setCenterId(m.getCenterId());
        entity.setArticleId(m.getArticleId());
        entity.setSeanceId(m.getSeanceId());
        entity.setLotId(m.getLotId());
        entity.setMouvementType(m.getMovementType().name());
        entity.setQuantite(m.getQuantite());
        entity.setPrixUnitaire(m.getPrixUnitaire());
        entity.setPmpApres(m.getPmpApres());
        entity.setCreatedBy(m.getCreatedBy());
        entity.setCreatedAt(m.getCreatedAt());
        return entity;
    }

    private StockMovement toDomain(StockMovementJpaEntity e) {
        StockMovement movement = new StockMovement();
        movement.setId(e.getId());
        movement.setCenterId(e.getCenterId());
        movement.setArticleId(e.getArticleId());
        movement.setSeanceId(e.getSeanceId());
        movement.setLotId(e.getLotId());
        movement.setMovementType(StockMovementType.valueOf(e.getMouvementType()));
        movement.setQuantite(e.getQuantite());
        movement.setPrixUnitaire(e.getPrixUnitaire());
        movement.setPmpApres(e.getPmpApres());
        movement.setCreatedBy(e.getCreatedBy());
        movement.setCreatedAt(e.getCreatedAt());
        return movement;
    }
}


