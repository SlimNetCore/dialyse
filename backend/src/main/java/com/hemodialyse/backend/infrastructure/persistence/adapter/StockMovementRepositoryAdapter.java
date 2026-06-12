package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.stock.model.StockMovement;
import com.hemodialyse.backend.domain.stock.port.StockMovementRepositoryPort;
import com.hemodialyse.backend.infrastructure.persistence.entity.StockMovementJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.StockMovementJpaRepository;
import org.springframework.stereotype.Component;

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

    private StockMovementJpaEntity toJpa(StockMovement m) {
        StockMovementJpaEntity entity = new StockMovementJpaEntity();
        entity.setId(m.getId());
        entity.setCenterId(m.getCenterId());
        entity.setArticleId(m.getArticleId());
        entity.setSeanceId(m.getSeanceId());
        entity.setMouvementType(m.getMovementType().name());
        entity.setQuantite(m.getQuantite());
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
        movement.setMovementType(com.hemodialyse.backend.domain.stock.model.StockMovementType.valueOf(e.getMouvementType()));
        movement.setQuantite(e.getQuantite());
        movement.setCreatedBy(e.getCreatedBy());
        movement.setCreatedAt(e.getCreatedAt());
        return movement;
    }
}

