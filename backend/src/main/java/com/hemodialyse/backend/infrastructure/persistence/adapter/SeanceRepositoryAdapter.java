package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceStatus;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.SeanceJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.SeanceJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class SeanceRepositoryAdapter implements SeanceRepositoryPort {

    private final SeanceJpaRepository jpa;

    public SeanceRepositoryAdapter(SeanceJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Seance save(Seance seance) {
        return toDomain(jpa.save(toJpa(seance)));
    }

    @Override
    public Optional<Seance> findById(UUID seanceId, CenterId centerId) {
        return jpa.findByIdAndCenterId(seanceId, centerId.value()).map(this::toDomain);
    }

    private Seance toDomain(SeanceJpaEntity e) {
        Seance s = new Seance();
        s.setId(e.getId());
        s.setPatientId(e.getPatientId());
        s.setCenterId(e.getCenterId());
        s.setDateSeance(e.getDateSeance());
        s.setStatus(SeanceStatus.valueOf(e.getStatut()));
        s.setCreatedAt(e.getCreatedAt());
        s.setValidatedAt(e.getValidatedAt());
        s.setSignedByInfirmierAt(e.getSignedInfirmierAt());
        s.setSignedByInfirmierUserId(e.getSignedInfirmierBy());
        s.setSignedByMedecinAt(e.getSignedMedecinAt());
        s.setSignedByMedecinUserId(e.getSignedMedecinBy());
        return s;
    }

    private SeanceJpaEntity toJpa(Seance s) {
        SeanceJpaEntity e = new SeanceJpaEntity();
        e.setId(s.getId());
        e.setPatientId(s.getPatientId());
        e.setCenterId(s.getCenterId());
        e.setDateSeance(s.getDateSeance());
        e.setStatut(s.getStatus().name());
        e.setCreatedAt(s.getCreatedAt());
        e.setValidatedAt(s.getValidatedAt());
        e.setSignedInfirmierAt(s.getSignedByInfirmierAt());
        e.setSignedInfirmierBy(s.getSignedByInfirmierUserId());
        e.setSignedMedecinAt(s.getSignedByMedecinAt());
        e.setSignedMedecinBy(s.getSignedByMedecinUserId());
        return e;
    }
}

