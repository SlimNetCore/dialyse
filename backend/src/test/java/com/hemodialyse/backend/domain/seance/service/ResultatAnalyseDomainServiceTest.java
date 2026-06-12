package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.seance.model.ResultatAnalyse;
import com.hemodialyse.backend.domain.seance.port.ResultatAnalyseRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResultatAnalyseDomainServiceTest {

    @Test
    void save_should_create_analyse_with_defaults() {
        InMemoryRepository repository = new InMemoryRepository();
        ResultatAnalyseDomainService service = new ResultatAnalyseDomainService(repository);

        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();

        ResultatAnalyse r = service.save(
                centerId,
                patientId,
                null,
                null,
                new BigDecimal("10.5"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertNotNull(r.getId());
        assertEquals(centerId.value(), r.getCenterId());
        assertEquals(patientId, r.getPatientId());
        assertEquals(LocalDate.now(), r.getDatePrelevement());
        assertNotNull(r.getCreatedAt());
        assertNotNull(r.getUpdatedAt());
    }

    @Test
    void listByPatient_should_return_desc_date() {
        InMemoryRepository repository = new InMemoryRepository();
        ResultatAnalyseDomainService service = new ResultatAnalyseDomainService(repository);

        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();

        ResultatAnalyse oldR = new ResultatAnalyse();
        oldR.setId(UUID.randomUUID());
        oldR.setCenterId(centerId.value());
        oldR.setPatientId(patientId);
        oldR.setDatePrelevement(LocalDate.of(2026, 4, 1));

        ResultatAnalyse newR = new ResultatAnalyse();
        newR.setId(UUID.randomUUID());
        newR.setCenterId(centerId.value());
        newR.setPatientId(patientId);
        newR.setDatePrelevement(LocalDate.of(2026, 5, 1));

        repository.save(oldR);
        repository.save(newR);

        List<ResultatAnalyse> items = service.listByPatient(centerId, patientId, null, null);
        assertEquals(2, items.size());
        assertEquals(newR.getId(), items.get(0).getId());
    }

    private static final class InMemoryRepository implements ResultatAnalyseRepositoryPort {
        private final List<ResultatAnalyse> data = new ArrayList<>();

        @Override
        public List<ResultatAnalyse> findByPatientId(UUID patientId, CenterId centerId, LocalDate from, LocalDate to) {
            return data.stream()
                    .filter(r -> patientId.equals(r.getPatientId()) && centerId.value().equals(r.getCenterId()))
                    .filter(r -> from == null || !r.getDatePrelevement().isBefore(from))
                    .filter(r -> to == null || !r.getDatePrelevement().isAfter(to))
                    .sorted(Comparator.comparing(ResultatAnalyse::getDatePrelevement).reversed())
                    .toList();
        }

        @Override
        public ResultatAnalyse save(ResultatAnalyse resultat) {
            data.removeIf(r -> r.getId().equals(resultat.getId()));
            data.add(resultat);
            return resultat;
        }

        @Override
        public void deleteById(UUID analyseId, UUID patientId, CenterId centerId) {
            data.removeIf(r -> analyseId.equals(r.getId())
                    && patientId.equals(r.getPatientId())
                    && centerId.value().equals(r.getCenterId()));
        }
    }
}

