package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.infrastructure.persistence.entity.AssureJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.AssureJpaRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AssureRepositoryAdapterTest {

    @Test
    void findByNumeroAssurance_should_map_entity_when_numero_matches_exactly() {
        AssureJpaRepository jpa = mock(AssureJpaRepository.class);
        AssureRepositoryAdapter adapter = new AssureRepositoryAdapter(jpa);
        AssureJpaEntity entity = new AssureJpaEntity();
        UUID centerId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();

        entity.setNumeroAssurance("ASS-123");
        entity.setCenterId(centerId);
        entity.setNom("Diallo");
        entity.setPrenom("Aminata");
        entity.setSexe("F");
        entity.setDateNaissance(LocalDate.of(1990, 5, 20));
        entity.setTelPersonnel("01020304");
        entity.setTelMobile("05060708");
        entity.setTelBureau("09101112");
        entity.setAdresse("Adresse test");
        entity.setGroupeSanguin("O+");
        entity.setCreatedAt(createdAt);

        when(jpa.findById("ASS-123")).thenReturn(Optional.of(entity));

        var result = adapter.findByNumeroAssurance("ASS-123");

        assertTrue(result.isPresent());
        assertEquals("ASS-123", result.get().getNumeroAssurance());
        assertEquals(centerId, result.get().getCenterId());
        assertEquals("Diallo", result.get().getNom());
        assertEquals("Aminata", result.get().getPrenom());
        assertEquals("F", result.get().getSexe());
        assertEquals(LocalDate.of(1990, 5, 20), result.get().getDateNaissance());
        assertEquals("01020304", result.get().getTelPersonnel());
        assertEquals("05060708", result.get().getTelMobile());
        assertEquals("09101112", result.get().getTelBureau());
        assertEquals("Adresse test", result.get().getAdresse());
        assertEquals("O+", result.get().getGroupeSanguin());
        assertEquals(createdAt, result.get().getCreatedAt());
        verify(jpa).findById("ASS-123");
    }

    @Test
    void findByNumeroAssurance_should_return_empty_without_querying_jpa_when_input_is_blank() {
        AssureJpaRepository jpa = mock(AssureJpaRepository.class);
        AssureRepositoryAdapter adapter = new AssureRepositoryAdapter(jpa);

        var result = adapter.findByNumeroAssurance("   ");

        assertTrue(result.isEmpty());
        verifyNoInteractions(jpa);
    }
}


