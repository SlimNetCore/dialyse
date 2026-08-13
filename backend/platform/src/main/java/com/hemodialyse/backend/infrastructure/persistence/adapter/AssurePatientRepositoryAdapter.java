package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.assure.model.AssurePatientAssignment;
import com.hemodialyse.backend.domain.assure.port.AssurePatientRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.AssurePatientJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.AssurePatientJpaRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AssurePatientRepositoryAdapter implements AssurePatientRepositoryPort {
    private final AssurePatientJpaRepository jpa;

    public AssurePatientRepositoryAdapter(AssurePatientJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "patient.assignment.primary", key = "#assignment.centerId.toString() + ':' + #assignment.patientId.toString()"),
            @CacheEvict(cacheNames = "patient.assignment.history", key = "#assignment.centerId.toString() + ':' + #assignment.patientId.toString()")
    })
    public AssurePatientAssignment save(AssurePatientAssignment assignment) {
        AssurePatientJpaEntity e = new AssurePatientJpaEntity();
        // Pour les mises à jour : conserver l'id existant
        if (assignment.getId() != null) e.setId(assignment.getId());
        e.setPatientId(assignment.getPatientId());
        e.setNumeroAssurance(assignment.getNumeroAssurance());
        e.setCenterId(assignment.getCenterId());
        e.setIsPrimary(assignment.isPrimary());
        e.setDateAffectation(assignment.getDateAffectation());
        e.setDateDebutAffectation(assignment.getDateDebutAffectation());
        e.setDateFinAffectation(assignment.getDateFinAffectation());
        return toDomain(jpa.save(e));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "patient.assignment.primary", key = "#centerId.value().toString() + ':' + #patientId.toString()"),
            @CacheEvict(cacheNames = "patient.assignment.history", key = "#centerId.value().toString() + ':' + #patientId.toString()")
    })
    public void clearPrimary(CenterId centerId, UUID patientId) {
        jpa.clearPrimary(centerId.value(), patientId);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "patient.assignment.primary", key = "#centerId.value().toString() + ':' + #patientId.toString()"),
            @CacheEvict(cacheNames = "patient.assignment.history", key = "#centerId.value().toString() + ':' + #patientId.toString()")
    })
    public void closePrimary(CenterId centerId, UUID patientId, LocalDate endDate) {
        jpa.closePrimary(centerId.value(), patientId, endDate);
    }

    @Override
    @Cacheable(
            cacheNames = "patient.assignment.primary",
            key = "#centerId.value().toString() + ':' + #patientId.toString()",
            unless = "#result == null || #result.isEmpty()"
    )
    public Optional<AssurePatientAssignment> findPrimary(CenterId centerId, UUID patientId) {
        return jpa.findFirstByCenterIdAndPatientIdAndIsPrimaryTrueOrderByDateAffectationDesc(centerId.value(), patientId)
            .map(this::toDomain);
    }

    @Override
    @Cacheable(cacheNames = "patient.assignment.history", key = "#centerId.value().toString() + ':' + #patientId.toString()")
    public List<AssurePatientAssignment> findHistory(CenterId centerId, UUID patientId) {
        return jpa.findByCenterIdAndPatientIdOrderByDateAffectationDesc(centerId.value(), patientId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<AssurePatientAssignment> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    private AssurePatientAssignment toDomain(AssurePatientJpaEntity e) {
        AssurePatientAssignment a = new AssurePatientAssignment();
        a.setId(e.getId());
        a.setPatientId(e.getPatientId());
        a.setNumeroAssurance(e.getNumeroAssurance());
        a.setCenterId(e.getCenterId());
        a.setPrimary(Boolean.TRUE.equals(e.getIsPrimary()));
        a.setDateAffectation(e.getDateAffectation());
        a.setDateDebutAffectation(e.getDateDebutAffectation());
        a.setDateFinAffectation(e.getDateFinAffectation());
        return a;
    }
}
