package com.hemodialyse.backend.domain.pec.service;

import com.hemodialyse.backend.domain.insurance.model.AttestationDroit;
import com.hemodialyse.backend.domain.insurance.port.AttestationRepositoryPort;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.model.PatientType;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.NumeroAssurance;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.pec.model.PecStatus;
import com.hemodialyse.backend.domain.pec.model.PriseEnCharge;
import com.hemodialyse.backend.domain.pec.port.PecRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PecDomainServiceTest {

    @Test
    void create_should_fail_when_patient_belongs_to_another_center() {
        CenterId requestedCenter = CenterId.of(UUID.randomUUID());
        CenterId patientCenter = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();

        InMemoryPecRepository pecRepo = new InMemoryPecRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository();
        patientRepo.addPatient(patientId, patientCenter, PatientType.NON_VACANCIER);

        PecDomainService service = new PecDomainService(pecRepo, patientRepo, new InMemoryAttestationRepository(true));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(requestedCenter, patientId, LocalDate.now(), LocalDate.now().plusDays(30), UUID.randomUUID())
        );
        assertTrue(ex.getMessage().contains("Patient introuvable"));
    }

    @Test
    void create_should_require_attestation_for_non_vacancier() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();

        InMemoryPecRepository pecRepo = new InMemoryPecRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository();
        patientRepo.addPatient(patientId, centerId, PatientType.NON_VACANCIER);

        PecDomainService service = new PecDomainService(pecRepo, patientRepo, new InMemoryAttestationRepository(false));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.create(centerId, patientId, LocalDate.now(), LocalDate.now().plusDays(15), UUID.randomUUID())
        );
        assertTrue(ex.getMessage().contains("Attestation valide obligatoire"));
    }

    @Test
    void create_should_not_require_attestation_for_vacancier() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();

        InMemoryPecRepository pecRepo = new InMemoryPecRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository();
        patientRepo.addPatient(patientId, centerId, PatientType.VACANCIER);

        PecDomainService service = new PecDomainService(pecRepo, patientRepo, new InMemoryAttestationRepository(false));

        PriseEnCharge created = service.create(centerId, patientId, LocalDate.now(), LocalDate.now().plusDays(10), UUID.randomUUID());

        assertEquals(centerId.value(), created.getCenterId());
        assertEquals(patientId, created.getPatientId());
        assertEquals(PecStatus.CREE, created.getStatus());
    }

    @Test
    void canCreateSession_should_return_false_when_non_vacancier_without_attestation() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID pecId = UUID.randomUUID();

        InMemoryPecRepository pecRepo = new InMemoryPecRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository();
        patientRepo.addPatient(patientId, centerId, PatientType.NON_VACANCIER);

        PriseEnCharge pec = new PriseEnCharge(pecId, patientId, centerId.value(), LocalDate.now(), LocalDate.now().plusDays(30), UUID.randomUUID());
        pec.valider(LocalDate.now(), LocalDate.now().plusDays(30), UUID.randomUUID());
        pecRepo.save(pec);

        PecDomainService service = new PecDomainService(pecRepo, patientRepo, new InMemoryAttestationRepository(false));

        assertFalse(service.canCreateSession(centerId, pecId));
    }

    private static final class InMemoryPecRepository implements PecRepositoryPort {
        private final Map<UUID, PriseEnCharge> byId = new HashMap<>();

        @Override
        public PriseEnCharge save(PriseEnCharge pec) {
            byId.put(pec.getId(), pec);
            return pec;
        }

        @Override
        public void deleteById(UUID id) {
            byId.remove(id);
        }

        @Override
        public Optional<PriseEnCharge> findById(UUID id, CenterId centerId) {
            return Optional.ofNullable(byId.get(id)).filter(pec -> pec.getCenterId().equals(centerId.value()));
        }

        @Override
        public List<PriseEnCharge> findByPatient(CenterId centerId, UUID patientId) {
            return byId.values().stream()
                    .filter(pec -> pec.getCenterId().equals(centerId.value()))
                    .filter(pec -> pec.getPatientId().equals(patientId))
                    .toList();
        }

        @Override
        public List<PriseEnCharge> findByCenter(CenterId centerId) {
            return byId.values().stream().filter(pec -> pec.getCenterId().equals(centerId.value())).toList();
        }
    }

    private static final class InMemoryPatientRepository implements PatientRepositoryPort {
        private final Map<UUID, Patient> byId = new HashMap<>();

        void addPatient(UUID patientId, CenterId centerId, PatientType type) {
            Patient patient = new Patient();
            patient.setId(PatientId.of(patientId));
            patient.setCenterId(centerId);
            patient.setNom("Nom");
            patient.setPrenom("Prenom");
            patient.setSexe("M");
            patient.setNumeroAssurance(new NumeroAssurance("ASS-" + patientId.toString().substring(0, 8)));
            patient.setTypePatient(type);
            byId.put(patientId, patient);
        }

        @Override
        public Patient save(Patient patient) {
            byId.put(patient.getId().value(), patient);
            return patient;
        }

        @Override
        public Optional<Patient> findById(PatientId id, CenterId centerId) {
            return Optional.ofNullable(byId.get(id.value())).filter(p -> p.getCenterId().equals(centerId));
        }

        @Override
        public Optional<Patient> findByCodePatient(CenterId centerId, String codePatient) {
            return Optional.empty();
        }

        @Override
        public Optional<Patient> findByNumeroAssurance(CenterId centerId, String numeroAssurance) {
            return Optional.empty();
        }

        @Override
        public List<Patient> findAllByCenter(CenterId centerId) {
            return byId.values().stream().filter(p -> p.getCenterId().equals(centerId)).toList();
        }

        @Override
        public long countByCenter(CenterId centerId) {
            return byId.values().stream().filter(p -> p.getCenterId().equals(centerId)).count();
        }
    }

    private record InMemoryAttestationRepository(boolean valid) implements AttestationRepositoryPort {

        @Override
            public AttestationDroit save(AttestationDroit a) {
                return a;
            }

            @Override
            public void deleteById(UUID id) {
                // No-op for unit tests.
            }

            @Override
            public boolean existsValidAt(CenterId centerId, UUID patientId, LocalDate date) {
                return valid;
            }

            @Override
            public List<AttestationDroit> findByPatient(CenterId centerId, UUID patientId) {
                return List.of();
            }
        }
}

