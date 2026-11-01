package com.hemodialyse.backend.domain.insurance.service;

import com.hemodialyse.backend.domain.insurance.model.AttestationDroit;
import com.hemodialyse.backend.domain.insurance.port.AttestationRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttestationDomainServiceTest {

    @Test
    void should_create_attestation() {
        InMemoryRepo repo = new InMemoryRepo();
        AttestationDomainService svc = new AttestationDomainService(repo);
        UUID patientId = UUID.randomUUID();
        UUID centerId = UUID.randomUUID();
        LocalDate debut = LocalDate.of(2026, 1, 1);
        LocalDate fin = LocalDate.of(2026, 12, 31);
        AttestationDroit a = svc.create(CenterId.of(centerId), patientId, debut, fin);
        assertEquals(patientId, a.getPatientId());
        assertEquals(centerId, a.getCenterId());
        assertEquals(1, repo.store.size());
    }

    @Test
    void should_reject_create_if_debut_after_fin() {
        InMemoryRepo repo = new InMemoryRepo();
        AttestationDomainService svc = new AttestationDomainService(repo);
        assertThrows(IllegalArgumentException.class, () ->
                svc.create(CenterId.of(UUID.randomUUID()), UUID.randomUUID(),
                        LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1)));
    }

    @Test
    void should_list_by_patient_scoped_by_center() {
        InMemoryRepo repo = new InMemoryRepo();
        AttestationDomainService svc = new AttestationDomainService(repo);
        UUID patientId = UUID.randomUUID();
        UUID centerA = UUID.randomUUID();
        UUID centerB = UUID.randomUUID();
        svc.create(CenterId.of(centerA), patientId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        svc.create(CenterId.of(centerB), patientId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertEquals(1, svc.listByPatient(CenterId.of(centerA), patientId).size());
        assertEquals(1, svc.listByPatient(CenterId.of(centerB), patientId).size());
    }

    @Test
    void should_delete_attestation() {
        InMemoryRepo repo = new InMemoryRepo();
        AttestationDomainService svc = new AttestationDomainService(repo);
        AttestationDroit a = svc.create(CenterId.of(UUID.randomUUID()), UUID.randomUUID(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        svc.delete(CenterId.of(a.getCenterId()), a.getId());
        assertTrue(repo.store.isEmpty());
    }

    private static class InMemoryRepo implements AttestationRepositoryPort {
        final List<AttestationDroit> store = new ArrayList<>();
        boolean existsValid = false;

        @Override
        public AttestationDroit save(AttestationDroit a) {
            store.add(a);
            return a;
        }

        @Override
        public void deleteById(UUID id) {
            store.removeIf(a -> a.getId().equals(id));
        }

        @Override
        public boolean existsValidAt(CenterId centerId, UUID patientId, LocalDate date) {
            return existsValid;
        }

        @Override
        public List<AttestationDroit> findByPatient(CenterId centerId, UUID patientId) {
            List<AttestationDroit> r = new ArrayList<>();
            for (AttestationDroit a : store) {
                if (a.getCenterId().equals(centerId.value()) && a.getPatientId().equals(patientId)) r.add(a);
            }
            return r;
        }
    }
}
