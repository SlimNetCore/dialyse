package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.seance.model.DossierMedicalPatient;
import com.hemodialyse.backend.domain.seance.port.DossierMedicalPatientRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DossierMedicalPatientDomainServiceTest {

    @Test
    void upsert_should_create_when_not_existing() {
        InMemoryRepository repository = new InMemoryRepository();
        DossierMedicalPatientDomainService service = new DossierMedicalPatientDomainService(repository);

        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();

        DossierMedicalPatient dossier = service.upsert(
                centerId,
                patientId,
                "GNMP",
                LocalDate.of(2020, 1, 15),
                "NEGATIF",
                "INCONNU",
                "RAS"
        );

        assertNotNull(dossier.getId());
        assertEquals(centerId.value(), dossier.getCenterId());
        assertEquals(patientId, dossier.getPatientId());
        assertEquals("GNMP", dossier.getNephropathieInitiale());
        assertNotNull(dossier.getCreatedAt());
        assertNotNull(dossier.getUpdatedAt());
    }

    @Test
    void getByPatient_should_return_existing() {
        InMemoryRepository repository = new InMemoryRepository();
        DossierMedicalPatientDomainService service = new DossierMedicalPatientDomainService(repository);

        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();

        DossierMedicalPatient d = new DossierMedicalPatient();
        d.setId(UUID.randomUUID());
        d.setCenterId(centerId.value());
        d.setPatientId(patientId);
        repository.save(d);

        Optional<DossierMedicalPatient> found = service.getByPatient(centerId, patientId);
        assertTrue(found.isPresent());
        assertEquals(d.getId(), found.get().getId());
    }

    private static final class InMemoryRepository implements DossierMedicalPatientRepositoryPort {
        private final Map<UUID, DossierMedicalPatient> dataByPatient = new HashMap<>();

        @Override
        public Optional<DossierMedicalPatient> findByPatientId(UUID patientId, CenterId centerId) {
            DossierMedicalPatient d = dataByPatient.get(patientId);
            if (d == null || !centerId.value().equals(d.getCenterId())) {
                return Optional.empty();
            }
            return Optional.of(d);
        }

        @Override
        public DossierMedicalPatient save(DossierMedicalPatient dossier) {
            dataByPatient.put(dossier.getPatientId(), dossier);
            return dossier;
        }
    }
}

