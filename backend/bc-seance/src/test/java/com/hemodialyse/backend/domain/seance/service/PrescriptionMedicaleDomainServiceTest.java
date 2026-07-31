package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.seance.model.PrescriptionMedicale;
import com.hemodialyse.backend.domain.seance.port.PrescriptionMedicaleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PrescriptionMedicaleDomainServiceTest {

    @Test
    void save_should_create_prescription_with_defaults() {
        InMemoryRepository repository = new InMemoryRepository();
        PrescriptionMedicaleDomainService service = new PrescriptionMedicaleDomainService(repository);

        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();

        PrescriptionMedicale p = service.save(
                centerId,
                patientId,
                null,
                null,
                UUID.randomUUID(),
                300,
                500,
                2500,
                240,
                "FX-80",
                "HNF",
                "Darbepoetine",
                60,
                "SC",
                "1x/sem",
                "Fer saccharose",
                100,
                "IV",
                "1x/sem"
        );

        assertNotNull(p.getId());
        assertEquals(centerId.value(), p.getCenterId());
        assertEquals(patientId, p.getPatientId());
        assertEquals(LocalDate.now(), p.getDatePrescription());
        assertNotNull(p.getCreatedAt());
        assertNotNull(p.getUpdatedAt());
    }

    @Test
    void listByPatient_should_return_sorted_items_from_repository() {
        InMemoryRepository repository = new InMemoryRepository();
        PrescriptionMedicaleDomainService service = new PrescriptionMedicaleDomainService(repository);

        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();

        PrescriptionMedicale oldP = new PrescriptionMedicale();
        oldP.setId(UUID.randomUUID());
        oldP.setCenterId(centerId.value());
        oldP.setPatientId(patientId);
        oldP.setDatePrescription(LocalDate.of(2026, 4, 1));

        PrescriptionMedicale newP = new PrescriptionMedicale();
        newP.setId(UUID.randomUUID());
        newP.setCenterId(centerId.value());
        newP.setPatientId(patientId);
        newP.setDatePrescription(LocalDate.of(2026, 5, 1));

        repository.save(oldP);
        repository.save(newP);

        List<PrescriptionMedicale> items = service.listByPatient(centerId, patientId, null, null);
        assertEquals(2, items.size());
        assertEquals(newP.getId(), items.get(0).getId());
    }

    @Test
    void delete_should_delegate_to_repository() {
        InMemoryRepository repository = new InMemoryRepository();
        PrescriptionMedicaleDomainService service = new PrescriptionMedicaleDomainService(repository);

        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();

        PrescriptionMedicale p = new PrescriptionMedicale();
        p.setId(prescriptionId);
        p.setCenterId(centerId.value());
        p.setPatientId(patientId);
        repository.save(p);

        service.delete(centerId, patientId, prescriptionId);
        assertEquals(0, repository.findByPatientId(patientId, centerId, null, null).size());
    }

    private static final class InMemoryRepository implements PrescriptionMedicaleRepositoryPort {
        private final List<PrescriptionMedicale> data = new ArrayList<>();

        @Override
        public List<PrescriptionMedicale> findByPatientId(UUID patientId, CenterId centerId, LocalDate from, LocalDate to) {
            return data.stream()
                    .filter(p -> patientId.equals(p.getPatientId()) && centerId.value().equals(p.getCenterId()))
                    .filter(p -> from == null || !p.getDatePrescription().isBefore(from))
                    .filter(p -> to == null || !p.getDatePrescription().isAfter(to))
                    .sorted(Comparator.comparing(PrescriptionMedicale::getDatePrescription).reversed())
                    .toList();
        }

        @Override
        public PrescriptionMedicale save(PrescriptionMedicale prescription) {
            data.removeIf(p -> p.getId().equals(prescription.getId()));
            data.add(prescription);
            return prescription;
        }

        @Override
        public void deleteById(UUID prescriptionId, UUID patientId, CenterId centerId) {
            data.removeIf(p -> prescriptionId.equals(p.getId())
                    && patientId.equals(p.getPatientId())
                    && centerId.value().equals(p.getCenterId()));
        }
    }
}

