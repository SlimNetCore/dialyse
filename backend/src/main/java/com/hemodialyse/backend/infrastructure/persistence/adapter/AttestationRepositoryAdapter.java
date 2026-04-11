package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.insurance.model.AttestationDroit;
import com.hemodialyse.backend.domain.insurance.port.AttestationRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.AttestationJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.AttestationJpaRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class AttestationRepositoryAdapter implements AttestationRepositoryPort {

    private final AttestationJpaRepository jpa;

    public AttestationRepositoryAdapter(AttestationJpaRepository jpa) { this.jpa = jpa; }

    @Override
    public AttestationDroit save(AttestationDroit a) {
        var e = new AttestationJpaEntity();
        e.setId(a.getId());
        e.setPatientId(a.getPatientId());
        e.setCenterId(a.getCenterId());
        e.setDateDebut(a.getDateDebut());
        e.setDateFin(a.getDateFin());
        e.setCreatedAt(a.getCreatedAt());
        var saved = jpa.save(e);
        return toDomain(saved);
    }

    @Override
    public void deleteById(UUID id) { jpa.deleteById(id); }

    @Override
    public boolean existsValidAt(CenterId centerId, UUID patientId, LocalDate date) {
        return jpa.existsValidAt(centerId.value(), patientId, date);
    }

    @Override
    public List<AttestationDroit> findByPatient(CenterId centerId, UUID patientId) {
        return jpa.findByCenterIdAndPatientId(centerId.value(), patientId).stream().map(this::toDomain).toList();
    }

    private AttestationDroit toDomain(AttestationJpaEntity e) {
        var a = new AttestationDroit(e.getId(), e.getPatientId(), e.getCenterId(), e.getDateDebut(), e.getDateFin());
        a.setCreatedAt(e.getCreatedAt());
        return a;
    }
}

