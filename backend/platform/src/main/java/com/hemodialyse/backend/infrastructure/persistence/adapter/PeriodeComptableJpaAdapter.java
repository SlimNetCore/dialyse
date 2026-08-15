package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.comptabilite.port.PeriodeComptableRepositoryPort;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.infrastructure.persistence.entity.PeriodeComptableJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.PeriodeComptableJpaRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

@Component
public class PeriodeComptableJpaAdapter implements PeriodeComptableRepositoryPort {

    private final PeriodeComptableJpaRepository repo;

    public PeriodeComptableJpaAdapter(PeriodeComptableJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public boolean isClotured(UUID centerId, YearMonth periode) {
        return repo.findByCenterIdAndAnneeAndMois(centerId, periode.getYear(), periode.getMonthValue())
                .map(PeriodeComptableJpaEntity::isCloturee)
                .orElse(false);
    }

    @Override
    public void cloturer(UUID centerId, YearMonth periode, String userId) {
        Optional<PeriodeComptableJpaEntity> existing = repo.findByCenterIdAndAnneeAndMois(
                centerId, periode.getYear(), periode.getMonthValue());
        if (existing.isPresent() && existing.get().isCloturee()) {
            throw new BusinessException("PERIODE_DEJA_CLOTUREE",
                    "La période " + periode + " est déjà clôturée");
        }
        PeriodeComptableJpaEntity entity = existing.orElseGet(() -> {
            PeriodeComptableJpaEntity e = new PeriodeComptableJpaEntity();
            e.setId(UUID.randomUUID());
            e.setCenterId(centerId);
            e.setAnnee(periode.getYear());
            e.setMois(periode.getMonthValue());
            return e;
        });
        entity.setCloturee(true);
        entity.setClotureeAt(OffsetDateTime.now());
        entity.setClotureeBy(userId);
        repo.save(entity);
    }
}

