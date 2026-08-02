package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.mapper.PatientMapper;
import com.hemodialyse.backend.infrastructure.persistence.repository.PatientJpaRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PatientRepositoryAdapter implements PatientRepositoryPort {

    private final PatientJpaRepository jpa;

    public PatientRepositoryAdapter(PatientJpaRepository jpa) { this.jpa = jpa; }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "patient.byId", key = "#patient.centerId.value().toString() + ':' + #patient.id.value().toString()"),
            @CacheEvict(cacheNames = "patient.byNumeroAssurance", allEntries = true),
            @CacheEvict(cacheNames = "patient.byCenter", key = "#patient.centerId.value().toString()"),
            @CacheEvict(cacheNames = "patient.countByCenter", key = "#patient.centerId.value().toString()")
    })
    public Patient save(Patient patient) {
        var entity = PatientMapper.toJpa(patient);
        var saved = jpa.save(entity);
        return PatientMapper.toDomain(saved);
    }

    @Override
    @Cacheable(
            cacheNames = "patient.byId",
            key = "#centerId.value().toString() + ':' + #id.value().toString()",
            unless = "#result == null"
    )
    public Optional<Patient> findById(PatientId id, CenterId centerId) {
        return Optional.ofNullable(jpa.findByIdAndCenterId(id.value(), centerId.value()))
                .flatMap(found -> found)
                .map(PatientMapper::toDomain);
    }

    @Override
    public Optional<Patient> findByCodePatient(CenterId centerId, String codePatient) {
        return Optional.ofNullable(jpa.findByCenterIdAndCodePatient(centerId.value(), codePatient))
                .flatMap(found -> found)
                .map(PatientMapper::toDomain);
    }

    @Override
    @Cacheable(
            cacheNames = "patient.byNumeroAssurance",
            key = "#centerId.value().toString() + ':' + (#numeroAssurance == null ? '' : #numeroAssurance.toLowerCase())",
            unless = "#result == null"
    )
    public Optional<Patient> findByNumeroAssurance(CenterId centerId, String numeroAssurance) {
        return Optional.ofNullable(jpa.findByCenterIdAndNumeroAssurance(centerId.value(), numeroAssurance))
                .flatMap(found -> found)
                .map(PatientMapper::toDomain);
    }

    @Override
    @Cacheable(cacheNames = "patient.byCenter", key = "#centerId.value().toString()")
    public List<Patient> findAllByCenter(CenterId centerId) {
        return jpa.findByCenterId(centerId.value()).stream().map(PatientMapper::toDomain).toList();
    }

    @Override
    @Cacheable(cacheNames = "patient.countByCenter", key = "#centerId.value().toString()")
    public long countByCenter(CenterId centerId) {
        return jpa.countByCenterId(centerId.value());
    }
}

