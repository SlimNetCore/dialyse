package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceStatus;
import com.hemodialyse.backend.domain.seance.model.VoletParamedical;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoletParamedicalDomainServiceTest {

    @Test
    void save_should_create_or_update_paramedical_sheet() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();

        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryVoletRepository voletRepo = new InMemoryVoletRepository();
        seanceRepo.save(new Seance(seanceId, patientId, centerId.value(), LocalDate.now()));

        VoletParamedicalDomainService service = new VoletParamedicalDomainService(seanceRepo, voletRepo);

        VoletParamedical volet = service.save(
                centerId,
                seanceId,
                new BigDecimal("72.5"),
                new BigDecimal("70.1"),
                "140/90",
                "130/80",
                240,
                300,
                new BigDecimal("2500"),
                "Heparine",
                "Bicarbonate",
                "RAS"
        );

        assertNotNull(volet.getId());
        assertEquals(seanceId, volet.getSeanceId());
        assertEquals("140/90", volet.getTaAvant());
        assertEquals(1, voletRepo.data.size());
    }

    @Test
    void save_should_fail_when_seance_is_facturee() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();

        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryVoletRepository voletRepo = new InMemoryVoletRepository();
        Seance seance = new Seance(seanceId, patientId, centerId.value(), LocalDate.now());
        seance.setStatus(SeanceStatus.FACTUREE);
        seanceRepo.save(seance);

        VoletParamedicalDomainService service = new VoletParamedicalDomainService(seanceRepo, voletRepo);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.save(
                centerId,
                seanceId,
                new BigDecimal("72.5"),
                new BigDecimal("70.1"),
                "140/90",
                "130/80",
                240,
                300,
                new BigDecimal("2500"),
                "Heparine",
                "Bicarbonate",
                "RAS"
        ));

        assertTrue(ex.getMessage().contains("facturee"));
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

        @Override
        public com.hemodialyse.backend.domain.shared.PagedResult<com.hemodialyse.backend.domain.seance.model.SeanceListItem> findPagedByCenter(CenterId centerId, int page, int size) {
            return new com.hemodialyse.backend.domain.shared.PagedResult<>(java.util.List.of(), 0, page, size);
        }

        @Override
        public com.hemodialyse.backend.domain.shared.PagedResult<com.hemodialyse.backend.domain.seance.model.SeanceListItem> findPagedByCenterAndMonth(CenterId centerId, java.time.YearMonth month, int page, int size) {
            return new com.hemodialyse.backend.domain.shared.PagedResult<>(java.util.List.of(), 0, page, size);
        }
    }

    private static final class InMemoryVoletRepository implements VoletParamedicalRepositoryPort {
        private final Map<UUID, VoletParamedical> data = new HashMap<>();

        @Override
        public Optional<VoletParamedical> findBySeanceId(UUID seanceId, CenterId centerId) {
            return data.values().stream()
                    .filter(v -> seanceId.equals(v.getSeanceId()) && centerId.value().equals(v.getCenterId()))
                    .findFirst();
        }

        @Override
        public VoletParamedical save(VoletParamedical volet) {
            data.put(volet.getId(), volet);
            return volet;
        }
    }
}

