package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.VoletMedical;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletMedicalRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VoletMedicalDomainServiceTest {

    @Test
    void save_should_create_medical_sheet_after_infirmier_validation() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();

        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryVoletRepository voletRepo = new InMemoryVoletRepository();
        Seance seance = new Seance(seanceId, patientId, centerId.value(), LocalDate.now());
        seance.validerParInfirmier("inf-01");
        seanceRepo.save(seance);

        VoletMedicalDomainService service = new VoletMedicalDomainService(seanceRepo, voletRepo);

        VoletMedical volet = service.save(
                centerId,
                seanceId,
                "Prescription test",
                "Bonne tolerance",
                "Examen normal",
                "Hb correcte",
                "Ajustement UF",
                "Conclusion favorable"
        );

        assertNotNull(volet.getId());
        assertEquals(seanceId, volet.getSeanceId());
        assertEquals("Prescription test", volet.getPrescription());
        assertEquals(1, voletRepo.data.size());
    }

    @Test
    void save_should_fail_when_seance_not_validated() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();

        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryVoletRepository voletRepo = new InMemoryVoletRepository();
        seanceRepo.save(new Seance(seanceId, patientId, centerId.value(), LocalDate.now()));

        VoletMedicalDomainService service = new VoletMedicalDomainService(seanceRepo, voletRepo);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.save(
                centerId,
                seanceId,
                "Prescription test",
                "Bonne tolerance",
                "Examen normal",
                "Hb correcte",
                "Ajustement UF",
                "Conclusion favorable"
        ));

        assertTrue(ex.getMessage().contains("apres validation infirmiere"));
    }

    private static final class InMemorySeanceRepository implements SeanceRepositoryPort {
        private final Map<UUID, Seance> data = new HashMap<>();

        @Override
        public Seance save(Seance seance) {
            data.put(seance.getId(), seance);
            return seance;
        }

        @Override
        public Optional<Seance> findById(UUID seanceId, CenterId centerId) {
            return Optional.ofNullable(data.get(seanceId)).filter(s -> s.getCenterId().equals(centerId.value()));
        }

        @Override
        public Optional<Seance> findByPatientIdAndDate(CenterId centerId, UUID patientId, java.time.LocalDate dateSeance) {
            return Optional.empty();
        }

        @Override
        public java.util.List<com.hemodialyse.backend.domain.seance.model.SeanceListItem> findAllByCenter(CenterId centerId) {
            return java.util.List.of();
        }
    }

    private static final class InMemoryVoletRepository implements VoletMedicalRepositoryPort {
        private final Map<UUID, VoletMedical> data = new HashMap<>();

        @Override
        public Optional<VoletMedical> findBySeanceId(UUID seanceId, CenterId centerId) {
            return data.values().stream()
                    .filter(v -> seanceId.equals(v.getSeanceId()) && centerId.value().equals(v.getCenterId()))
                    .findFirst();
        }

        @Override
        public VoletMedical save(VoletMedical volet) {
            data.put(volet.getId(), volet);
            return volet;
        }
    }
}

