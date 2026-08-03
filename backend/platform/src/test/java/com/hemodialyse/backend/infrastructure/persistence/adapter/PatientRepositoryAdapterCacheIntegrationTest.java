package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.model.PatientType;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.NumeroAssurance;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.repository.PatientJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PatientRepositoryAdapterCacheIntegrationTest {

    @Autowired
    private PatientRepositoryPort patientRepositoryPort;

    @Autowired
    private PatientJpaRepository patientJpaRepository;

    @Autowired
    private CacheManager cacheManager;

    private static Patient buildPatient(CenterId centerId, String numeroAssurance) {
        return Patient.creer(
                centerId,
                "Diallo",
                "Aminata",
                "F",
                LocalDate.now(),
                LocalDate.of(1990, 1, 5),
                new NumeroAssurance(numeroAssurance),
                PatientType.NON_VACANCIER
        );
    }

    @BeforeEach
    void setUp() {
        patientJpaRepository.deleteAll();
        Objects.requireNonNull(cacheManager.getCache("patient.byId")).clear();
        Objects.requireNonNull(cacheManager.getCache("patient.byNumeroAssurance")).clear();
        Objects.requireNonNull(cacheManager.getCache("patient.byCenter")).clear();
        Objects.requireNonNull(cacheManager.getCache("patient.countByCenter")).clear();
    }

    @Test
    void findById_should_not_leak_cached_patient_across_centers() {
        CenterId centerA = CenterId.of(UUID.randomUUID());
        CenterId centerB = CenterId.of(UUID.randomUUID());
        Patient patient = buildPatient(centerA, "ASS-CACHE-01");
        patientRepositoryPort.save(patient);

        var byCenterA = patientRepositoryPort.findById(patient.getId(), centerA);
        var byCenterB = patientRepositoryPort.findById(patient.getId(), centerB);

        assertTrue(byCenterA.isPresent());
        assertTrue(byCenterB.isEmpty());
    }

    @Test
    void findByNumeroAssurance_should_not_leak_cached_patient_across_centers() {
        CenterId centerA = CenterId.of(UUID.randomUUID());
        CenterId centerB = CenterId.of(UUID.randomUUID());
        String numeroAssurance = "ASS-CACHE-02";

        patientRepositoryPort.save(buildPatient(centerA, numeroAssurance));

        var inCenterA = patientRepositoryPort.findByNumeroAssurance(centerA, numeroAssurance);
        var inCenterB = patientRepositoryPort.findByNumeroAssurance(centerB, numeroAssurance);

        assertTrue(inCenterA.isPresent());
        assertTrue(inCenterB.isEmpty());
    }
}

