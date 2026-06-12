package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Emplacement;
import com.hemodialyse.backend.domain.stock.port.EmplacementRepositoryPort;
import com.hemodialyse.backend.infrastructure.persistence.entity.EmplacementJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.EmplacementJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class EmplacementRepositoryAdapter implements EmplacementRepositoryPort {

    private final EmplacementJpaRepository jpa;

    public EmplacementRepositoryAdapter(EmplacementJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Emplacement save(Emplacement em) {
        return toDomain(jpa.save(toJpa(em)));
    }

    @Override
    public Optional<Emplacement> findById(UUID id, CenterId centerId) {
        return jpa.findByIdAndCenterId(id, centerId.value()).map(this::toDomain);
    }

    @Override
    public List<Emplacement> findAllActive(CenterId centerId) {
        return jpa.findByCenterIdAndActifTrueOrderByLibelleAsc(centerId.value())
                .stream().map(this::toDomain).toList();
    }

    private EmplacementJpaEntity toJpa(Emplacement em) {
        EmplacementJpaEntity e = new EmplacementJpaEntity();
        e.setId(em.id());
        e.setCenterId(em.centerId());
        e.setCode(em.code());
        e.setLibelle(em.libelle());
        e.setActif(em.actif());
        return e;
    }

    private Emplacement toDomain(EmplacementJpaEntity e) {
        return new Emplacement(e.getId(), e.getCenterId(), e.getCode(), e.getLibelle(), e.isActif());
    }
}

