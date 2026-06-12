package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Lot;
import com.hemodialyse.backend.domain.stock.port.LotRepositoryPort;
import com.hemodialyse.backend.infrastructure.persistence.entity.LotJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.LotJpaRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class LotRepositoryAdapter implements LotRepositoryPort {

    private final LotJpaRepository jpa;

    public LotRepositoryAdapter(LotJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Lot save(Lot lot) {
        return toDomain(jpa.save(toJpa(lot)));
    }

    @Override
    public Optional<Lot> findById(UUID id, CenterId centerId) {
        return jpa.findByIdAndCenterId(id, centerId.value()).map(this::toDomain);
    }

    @Override
    public List<Lot> findAvailableByArticleFefo(UUID articleId, CenterId centerId) {
        return jpa.findAvailableFefo(centerId.value(), articleId, BigDecimal.ZERO)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Lot> findExpiringBefore(CenterId centerId, LocalDate threshold) {
        return jpa.findExpiring(centerId.value(), threshold, BigDecimal.ZERO)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Lot> findByArticle(UUID articleId, CenterId centerId) {
        return jpa.findByCenterIdAndArticleId(centerId.value(), articleId)
                .stream().map(this::toDomain).toList();
    }

    private LotJpaEntity toJpa(Lot l) {
        LotJpaEntity e = new LotJpaEntity();
        e.setId(l.getId());
        e.setCenterId(l.getCenterId());
        e.setArticleId(l.getArticleId());
        e.setBonReceptionId(l.getBonReceptionId());
        e.setEmplacementId(l.getEmplacementId());
        e.setNumeroLot(l.getNumeroLot());
        e.setDatePeremption(l.getDatePeremption());
        e.setQuantiteInitiale(l.getQuantiteInitiale());
        e.setQuantiteRestante(l.getQuantiteRestante());
        e.setPmp(l.getPmp());
        e.setCreatedAt(l.getCreatedAt());
        return e;
    }

    private Lot toDomain(LotJpaEntity e) {
        Lot l = new Lot();
        l.setId(e.getId());
        l.setCenterId(e.getCenterId());
        l.setArticleId(e.getArticleId());
        l.setBonReceptionId(e.getBonReceptionId());
        l.setEmplacementId(e.getEmplacementId());
        l.setNumeroLot(e.getNumeroLot());
        l.setDatePeremption(e.getDatePeremption());
        l.setQuantiteInitiale(e.getQuantiteInitiale());
        l.setQuantiteRestante(e.getQuantiteRestante());
        l.setPmp(e.getPmp());
        l.setCreatedAt(e.getCreatedAt());
        return l;
    }
}

