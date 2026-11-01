package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffeId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffeRepository;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.BilanPreGreffeComplete;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BilanPreGreffeServiceTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final UUID PATIENT = UUID.fromString("99997300-0000-0000-0000-000000000101");
    private static final Instant MAINTENANT = Instant.parse("2026-03-10T08:00:00Z");
    private static final LocalDate DATE_DEMARRAGE = LocalDate.of(2026, 3, 10);

    private BilanPreGreffeService nouveauService() {
        return new BilanPreGreffeService(new FakeBilanRepository(),
                () -> UUID.fromString("99997300-0000-0000-0000-000000000200"),
                evenement -> {
                });
    }

    private List<EtapeBilan> deuxEtapes() {
        return List.of(EtapeBilan.aFaire("CARDIO", "Bilan cardiaque"),
                EtapeBilan.aFaire("NEPHRO", "Avis néphrologique"));
    }

    @Test
    void demarrer_bilan() {
        BilanPreGreffeService service = nouveauService();
        BilanPreGreffeId id = service.demarrer(
                new BilanPreGreffeUseCases.DemarrerBilanCommande(
                        PATIENT, CENTRE, deuxEtapes(), DATE_DEMARRAGE, null), MAINTENANT);
        assertNotNull(id);
        assertEquals(id.value(), UUID.fromString("99997300-0000-0000-0000-000000000200"));
    }

    @Test
    void rejeter_commande_nulle() {
        BilanPreGreffeService service = nouveauService();
        assertThrows(IllegalArgumentException.class, () ->
                service.demarrer(null, MAINTENANT));
    }

    @Test
    void rejeter_maintenant_nul() {
        BilanPreGreffeService service = nouveauService();
        assertThrows(IllegalArgumentException.class, () ->
                service.demarrer(new BilanPreGreffeUseCases.DemarrerBilanCommande(
                        PATIENT, CENTRE, deuxEtapes(), DATE_DEMARRAGE, null), null));
    }

    @Test
    void valider_etape_puis_completer_publie_evenement() {
        CapturingEventConsumer consumer = new CapturingEventConsumer();
        FakeBilanRepository repo = new FakeBilanRepository();
        BilanPreGreffeService service = new BilanPreGreffeService(repo,
                () -> UUID.fromString("99997300-0000-0000-0000-000000000200"), consumer);
        BilanPreGreffeId id = service.demarrer(
                new BilanPreGreffeUseCases.DemarrerBilanCommande(
                        PATIENT, CENTRE, deuxEtapes(), DATE_DEMARRAGE, null), MAINTENANT);
        service.validerEtape(id, CENTRE, "CARDIO", "MED-001", MAINTENANT);
        service.validerEtape(id, CENTRE, "NEPHRO", "MED-002", MAINTENANT);
        BilanPreGreffe complet = service.completer(id, CENTRE, MAINTENANT);
        assertTrue(complet.estComplet());
        assertNotNull(consumer.dernier);
        assertEquals(id.value(), consumer.dernier.bilanId());
    }

    @Test
    void consulter_inexistant_lance_exception() {
        BilanPreGreffeService service = nouveauService();
        assertThrows(IllegalArgumentException.class, () ->
                service.consulter(new BilanPreGreffeId(UUID.randomUUID()), CENTRE));
    }

    @Test
    void lister_par_patient_pagine_et_filtre_centre() {
        BilanPreGreffeService service = nouveauService();
        PagedResult<BilanPreGreffe> page = service.listerParPatient(CENTRE, PATIENT, 0, 20);
        assertEquals(0, page.total());
        assertEquals(0, page.items().size());
    }

    private static class FakeBilanRepository implements BilanPreGreffeRepository {
        private final List<BilanPreGreffe> stock = new ArrayList<>();

        @Override
        public void save(BilanPreGreffe bilan) {
            stock.removeIf(b -> b.id().equals(bilan.id()));
            stock.add(bilan);
        }

        @Override
        public Optional<BilanPreGreffe> trouver(BilanPreGreffeId id, CentreId centreId) {
            return stock.stream()
                    .filter(b -> b.id().equals(id) && b.centreId().equals(centreId))
                    .findFirst();
        }

        @Override
        public PagedResult<BilanPreGreffe> listerParPatient(CentreId centreId, UUID patientId, int page, int size) {
            List<BilanPreGreffe> filtres = stock.stream()
                    .filter(b -> b.centreId().equals(centreId) && b.patientId().equals(patientId))
                    .toList();
            int from = Math.min(page * size, filtres.size());
            int to = Math.min(from + size, filtres.size());
            return PagedResult.of(filtres.subList(from, to), filtres.size(), page, size);
        }
    }

    private static class CapturingEventConsumer implements Consumer<BilanPreGreffeComplete> {
        private BilanPreGreffeComplete dernier;

        @Override
        public void accept(BilanPreGreffeComplete evenement) {
            this.dernier = evenement;
        }
    }
}
