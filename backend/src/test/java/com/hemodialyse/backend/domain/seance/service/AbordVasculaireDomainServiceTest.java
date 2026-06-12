package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.seance.model.AbordVasculaire;
import com.hemodialyse.backend.domain.seance.port.AbordVasculaireRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AbordVasculaireDomainServiceTest {

    @Test
    void save_should_create_abord_with_defaults() {
        InMemoryRepository repository = new InMemoryRepository();
        AbordVasculaireDomainService service = new AbordVasculaireDomainService(repository);

        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();

        AbordVasculaire abord = service.save(
                centerId,
                patientId,
                null,
                "FAV",
                "GAUCHE",
                "Avant-bras",
                LocalDate.of(2026, 5, 1),
                null,
                null,
                null
        );

        assertNotNull(abord.getId());
        assertEquals(centerId.value(), abord.getCenterId());
        assertEquals(patientId, abord.getPatientId());
        assertEquals("FAV", abord.getTypeAbord());
        assertTrue(abord.getActif());
        assertNotNull(abord.getCreatedAt());
    }

    @Test
    void listByPatient_should_delegate_to_repository() {
        InMemoryRepository repository = new InMemoryRepository();
        AbordVasculaireDomainService service = new AbordVasculaireDomainService(repository);

        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();

        AbordVasculaire a = new AbordVasculaire();
        a.setId(UUID.randomUUID());
        a.setCenterId(centerId.value());
        a.setPatientId(patientId);
        repository.save(a);

        List<AbordVasculaire> items = service.listByPatient(centerId, patientId);
        assertEquals(1, items.size());
        assertEquals(a.getId(), items.get(0).getId());
    }

    private static final class InMemoryRepository implements AbordVasculaireRepositoryPort {
        private final List<AbordVasculaire> data = new ArrayList<>();

        @Override
        public List<AbordVasculaire> findByPatientId(UUID patientId, CenterId centerId) {
            return data.stream()
                    .filter(a -> patientId.equals(a.getPatientId()) && centerId.value().equals(a.getCenterId()))
                    .toList();
        }

        @Override
        public AbordVasculaire save(AbordVasculaire abord) {
            data.removeIf(a -> a.getId().equals(abord.getId()));
            data.add(abord);
            return abord;
        }
    }
}

