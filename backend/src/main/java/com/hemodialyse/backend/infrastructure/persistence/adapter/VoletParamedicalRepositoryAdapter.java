package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.seance.model.VoletParamedical;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.VoletParamedicalJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.VoletParamedicalJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class VoletParamedicalRepositoryAdapter implements VoletParamedicalRepositoryPort {

    private final VoletParamedicalJpaRepository jpa;

    public VoletParamedicalRepositoryAdapter(VoletParamedicalJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<VoletParamedical> findBySeanceId(UUID seanceId, CenterId centerId) {
        return jpa.findBySeanceIdAndCenterId(seanceId, centerId.value()).map(this::toDomain);
    }

    @Override
    public VoletParamedical save(VoletParamedical volet) {
        return toDomain(jpa.save(toJpa(volet)));
    }

    private VoletParamedical toDomain(VoletParamedicalJpaEntity e) {
        VoletParamedical v = new VoletParamedical();
        v.setId(e.getId());
        v.setSeanceId(e.getSeanceId());
        v.setCenterId(e.getCenterId());
        v.setPoidsAvantKg(e.getPoidsAvantKg());
        v.setPoidsApresKg(e.getPoidsApresKg());
        v.setTaAvant(e.getTaAvant());
        v.setTaApres(e.getTaApres());
        v.setDureeMinutes(e.getDureeMinutes());
        v.setDebitSangMlMin(e.getDebitSangMlMin());
        v.setUltrafiltrationMl(e.getUltrafiltrationMl());
        v.setAnticoagulant(e.getAnticoagulant());
        v.setTypeDialysat(e.getTypeDialysat());
        v.setIncidents(e.getIncidents());
        v.setCreatedAt(e.getCreatedAt());
        v.setUpdatedAt(e.getUpdatedAt());
        return v;
    }

    private VoletParamedicalJpaEntity toJpa(VoletParamedical v) {
        VoletParamedicalJpaEntity e = new VoletParamedicalJpaEntity();
        e.setId(v.getId());
        e.setSeanceId(v.getSeanceId());
        e.setCenterId(v.getCenterId());
        e.setPoidsAvantKg(v.getPoidsAvantKg());
        e.setPoidsApresKg(v.getPoidsApresKg());
        e.setTaAvant(v.getTaAvant());
        e.setTaApres(v.getTaApres());
        e.setDureeMinutes(v.getDureeMinutes());
        e.setDebitSangMlMin(v.getDebitSangMlMin());
        e.setUltrafiltrationMl(v.getUltrafiltrationMl());
        e.setAnticoagulant(v.getAnticoagulant());
        e.setTypeDialysat(v.getTypeDialysat());
        e.setIncidents(v.getIncidents());
        e.setCreatedAt(v.getCreatedAt());
        e.setUpdatedAt(v.getUpdatedAt());
        return e;
    }
}

