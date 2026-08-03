package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.patient.model.PatientType;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.PatientJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.PatientJpaRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatientRepositoryAdapterTest {

    private static PatientJpaEntity buildEntity(UUID patientId, UUID centerId, String codePatient, String numeroAssurance) {
        PatientJpaEntity entity = new PatientJpaEntity();
        entity.setId(patientId);
        entity.setCenterId(centerId);
        entity.setCodePatient(codePatient);
        entity.setNom("Nom");
        entity.setPrenom("Prenom");
        entity.setSexe("M");
        entity.setDateAdmission(LocalDate.now());
        entity.setNumeroAssurance(numeroAssurance);
        entity.setTypePatient(PatientType.NON_VACANCIER.name());
        entity.setCreatedAt(OffsetDateTime.now());
        return entity;
    }

    @Test
    void findById_should_query_by_patient_and_center_and_map_result() {
        PatientJpaRepository jpa = mock(PatientJpaRepository.class);
        PatientRepositoryAdapter adapter = new PatientRepositoryAdapter(jpa);

        UUID patientId = UUID.randomUUID();
        UUID centerId = UUID.randomUUID();
        PatientJpaEntity entity = buildEntity(patientId, centerId, "PAT-100", "ASS-100");

        when(jpa.findByIdAndCenterId(patientId, centerId)).thenReturn(Optional.of(entity));

        var result = adapter.findById(PatientId.of(patientId), CenterId.of(centerId));

        assertTrue(result.isPresent());
        assertEquals(patientId, result.get().getId().value());
        assertEquals(centerId, result.get().getCenterId().value());
        assertEquals("PAT-100", result.get().getCodePatient());
        assertEquals("ASS-100", result.get().getNumeroAssurance().value());
        verify(jpa).findByIdAndCenterId(patientId, centerId);
    }

    @Test
    void findAllByCenter_should_only_map_entities_of_requested_center() {
        PatientJpaRepository jpa = mock(PatientJpaRepository.class);
        PatientRepositoryAdapter adapter = new PatientRepositoryAdapter(jpa);

        UUID centerId = UUID.randomUUID();
        when(jpa.findByCenterId(centerId)).thenReturn(List.of(
                buildEntity(UUID.randomUUID(), centerId, "PAT-201", "ASS-201"),
                buildEntity(UUID.randomUUID(), centerId, "PAT-202", "ASS-202")
        ));

        var result = adapter.findAllByCenter(CenterId.of(centerId));

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(p -> p.getCenterId().value().equals(centerId)));
        verify(jpa).findByCenterId(centerId);
    }
}

