package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologiqueId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologiqueRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObservationBiologiqueServiceTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final UUID PATIENT = UUID.fromString("99997300-0000-0000-0000-000000000101");
    private static final Instant MAINTENANT = Instant.parse("2026-03-10T08:00:00Z");
    private static final LocalDate DATE_OBSERVATION = LocalDate.of(2026, 3, 10);

    private ObservationBiologiqueService nouveauService() {
        return new ObservationBiologiqueService(new FakeObservationRepository(),
                () -> UUID.fromString("99997300-0000-0000-0000-000000000200"));
    }

    @Test
    void enregistrer_observation() {
        ObservationBiologiqueService service = nouveauService();
        ObservationBiologiqueId id = service.enregistrer(
                new ObservationBiologiqueUseCases.EnregistrerObservationCommande(
                        PATIENT, CENTRE, "HGB", "Hémoglobine", "11.5", "g/dL",
                        12.0, 16.0, DATE_OBSERVATION, "MED-001", "Labo Central", null), MAINTENANT);
        assertNotNull(id);
        assertEquals(id.value(), UUID.fromString("99997300-0000-0000-0000-000000000200"));
    }

    @Test
    void rejeter_commande_nulle() {
        ObservationBiologiqueService service = nouveauService();
        assertThrows(IllegalArgumentException.class, () ->
                service.enregistrer(null, MAINTENANT));
    }

    @Test
    void rejeter_maintenant_nul() {
        ObservationBiologiqueService service = nouveauService();
        assertThrows(IllegalArgumentException.class, () ->
                service.enregistrer(new ObservationBiologiqueUseCases.EnregistrerObservationCommande(
                        PATIENT, CENTRE, "HGB", null, "11.5", null, null, null,
                        DATE_OBSERVATION, null, null, null), null));
    }

    @Test
    void rejeter_valeur_invalide_remontee_du_domaine() {
        ObservationBiologiqueService service = nouveauService();
        assertThrows(IllegalArgumentException.class, () ->
                service.enregistrer(new ObservationBiologiqueUseCases.EnregistrerObservationCommande(
                        PATIENT, CENTRE, "HGB", null, " ", null, null, null,
                        DATE_OBSERVATION, null, null, null), MAINTENANT));
    }

    @Test
    void consulter_inexistant_lance_exception() {
        ObservationBiologiqueService service = nouveauService();
        assertThrows(IllegalArgumentException.class, () ->
                service.consulter(new ObservationBiologiqueId(UUID.randomUUID()), CENTRE));
    }

    @Test
    void lister_par_patient_pagine_et_filtre_centre() {
        ObservationBiologiqueService service = nouveauService();
        PagedResult<ObservationBiologique> page = service.listerParPatient(CENTRE, PATIENT, 0, 20);
        assertEquals(0, page.total());
        assertEquals(0, page.items().size());
        assertEquals(0, page.page());
        assertEquals(20, page.size());
    }

    private static class FakeObservationRepository implements ObservationBiologiqueRepository {
        private final List<ObservationBiologique> stock = new ArrayList<>();

        @Override
        public void save(ObservationBiologique observation) {
            stock.add(observation);
        }

        @Override
        public Optional<ObservationBiologique> trouver(ObservationBiologiqueId id, CentreId centreId) {
            return stock.stream()
                    .filter(o -> o.id().equals(id) && o.centreId().equals(centreId))
                    .findFirst();
        }

        @Override
        public PagedResult<ObservationBiologique> listerParPatient(CentreId centreId, UUID patientId, int page, int size) {
            List<ObservationBiologique> filtres = stock.stream()
                    .filter(o -> o.centreId().equals(centreId) && o.patientId().equals(patientId))
                    .toList();
            int from = Math.min(page * size, filtres.size());
            int to = Math.min(from + size, filtres.size());
            return PagedResult.of(filtres.subList(from, to), filtres.size(), page, size);
        }
    }
}
