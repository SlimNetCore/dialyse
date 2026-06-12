package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.seance.model.AbordVasculaire;
import com.hemodialyse.backend.domain.seance.port.AbordVasculaireRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.AbordVasculaireJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.AbordVasculaireJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class AbordVasculaireRepositoryAdapter implements AbordVasculaireRepositoryPort {

    private final AbordVasculaireJpaRepository jpa;

    public AbordVasculaireRepositoryAdapter(AbordVasculaireJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<AbordVasculaire> findByPatientId(UUID patientId, CenterId centerId) {
        return jpa.findByPatientIdAndCenterIdOrderByDateCreationDesc(patientId, centerId.value())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public AbordVasculaire save(AbordVasculaire abord) {
        return toDomain(jpa.save(toJpa(abord)));
    }

    private AbordVasculaire toDomain(AbordVasculaireJpaEntity e) {
        AbordVasculaire a = new AbordVasculaire();
        a.setId(e.getId());
        a.setPatientId(e.getPatientId());
        a.setCenterId(e.getCenterId());
        a.setTypeAbord(e.getTypeAbord());
        a.setCote(e.getCote());
        a.setLocalisation(e.getLocalisation());
        a.setDateCreation(e.getDateCreation());
        a.setDateFin(e.getDateFin());
        a.setActif(e.getActif());
        a.setComplications(e.getComplications());
        a.setCreatedAt(e.getCreatedAt());
        return a;
    }

    private AbordVasculaireJpaEntity toJpa(AbordVasculaire a) {
        AbordVasculaireJpaEntity e = new AbordVasculaireJpaEntity();
        e.setId(a.getId());
        e.setPatientId(a.getPatientId());
        e.setCenterId(a.getCenterId());
        e.setTypeAbord(a.getTypeAbord());
        e.setCote(a.getCote());
        e.setLocalisation(a.getLocalisation());
        e.setDateCreation(a.getDateCreation());
        e.setDateFin(a.getDateFin());
        e.setActif(a.getActif());
        e.setComplications(a.getComplications());
        e.setCreatedAt(a.getCreatedAt());
        return e;
    }
}

