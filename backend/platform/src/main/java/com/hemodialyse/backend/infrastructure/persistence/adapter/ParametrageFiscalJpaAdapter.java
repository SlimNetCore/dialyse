package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.comptabilite.port.ParametrageFiscalPort;
import com.hemodialyse.backend.domain.comptabilite.valueobject.RegleTVA;
import com.hemodialyse.backend.infrastructure.persistence.entity.RegleTVAJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.RegleTVAJpaRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ParametrageFiscalJpaAdapter implements ParametrageFiscalPort {

    private final RegleTVAJpaRepository repo;

    public ParametrageFiscalJpaAdapter(RegleTVAJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Optional<RegleTVA> findActiveAt(UUID centerId, String typePrestation, LocalDate date) {
        // Cherche règle avec dateFinValidite <= date
        Optional<RegleTVAJpaEntity> found = repo
                .findTopByCenterIdAndTypePrestationAndDateDebutValiditeLessThanEqualAndDateFinValiditeGreaterThanEqualOrderByDateDebutValiditeDesc(
                        centerId, typePrestation, date, date);
        if (found.isEmpty()) {
            // Cherche règle sans fin de validité (toujours en vigueur)
            found = repo.findTopByCenterIdAndTypePrestationAndDateDebutValiditeLessThanEqualAndDateFinValiditeIsNullOrderByDateDebutValiditeDesc(
                    centerId, typePrestation, date);
        }
        return found.map(this::toDomain);
    }

    @Override
    public List<RegleTVA> findAll(UUID centerId) {
        return repo.findByCenterIdOrderByDateDebutValiditeDesc(centerId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void save(UUID centerId, RegleTVA regle) {
        RegleTVAJpaEntity entity = new RegleTVAJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setCenterId(centerId);
        entity.setTypePrestation(regle.typePrestation());
        entity.setTauxApplique(regle.tauxApplique());
        entity.setExonere(regle.exonere());
        entity.setDateDebutValidite(regle.dateDebutValidite());
        entity.setDateFinValidite(regle.dateFinValidite());
        entity.setTexteReference(regle.texteReference());
        repo.save(entity);
    }

    private RegleTVA toDomain(RegleTVAJpaEntity e) {
        return new RegleTVA(e.getTypePrestation(), e.getTauxApplique(), e.isExonere(),
                e.getDateDebutValidite(), e.getDateFinValidite(), e.getTexteReference());
    }
}

