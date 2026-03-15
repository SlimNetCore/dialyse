package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.mapper.PatientMapper;
import com.hemodialyse.backend.infrastructure.persistence.repository.PatientJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PatientRepositoryAdapter implements PatientRepositoryPort {

    private final PatientJpaRepository jpa;

    public PatientRepositoryAdapter(PatientJpaRepository jpa) { this.jpa = jpa; }

    @Override
    public Patient save(Patient patient) {
        var entity = PatientMapper.toJpa(patient);
        var saved = jpa.save(entity);
        return PatientMapper.toDomain(saved);
    }

    @Override
    public Optional<Patient> findById(PatientId id, CenterId centerId) {
        return jpa.findByIdAndCenterId(id.value(), centerId.value()).map(PatientMapper::toDomain);
    }

    @Override
    public Optional<Patient> findByNumeroAssurance(CenterId centerId, String numeroAssurance) {
        return jpa.findByCenterIdAndNumeroAssurance(centerId.value(), numeroAssurance).map(PatientMapper::toDomain);
    }

    @Override
    public List<Patient> findAllByCenter(CenterId centerId) {
        return jpa.findByCenterId(centerId.value()).stream().map(PatientMapper::toDomain).toList();
    }

    @Override
    public long countByCenter(CenterId centerId) {
        return jpa.countByCenterId(centerId.value());
    }
}

