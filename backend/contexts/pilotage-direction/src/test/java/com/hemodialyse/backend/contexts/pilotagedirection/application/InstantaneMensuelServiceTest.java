package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuelId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuelRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.InstantaneMensuelCapture;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.Mois;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstantaneMensuelServiceTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final Instant MAINTENANT = Instant.parse("2026-03-31T23:00:00Z");

    private InstantaneMensuelService nouveauService() {
        return new InstantaneMensuelService(new FakeInstantaneRepository(),
                () -> UUID.fromString("99997300-0000-0000-0000-000000000200"),
                evenement -> {
                });
    }

    @Test
    void capturer_instantane_publie_evenement() {
        CapturingEventConsumer consumer = new CapturingEventConsumer();
        FakeInstantaneRepository repo = new FakeInstantaneRepository();
        InstantaneMensuelService service = new InstantaneMensuelService(repo,
                () -> UUID.fromString("99997300-0000-0000-0000-000000000200"), consumer);
        InstantaneMensuelId id = service.capturer(
                new InstantaneMensuelUseCases.CapturerInstantaneCommande(
                        CENTRE, Mois.de(2026, 3), "{\"seances\":120}"), MAINTENANT);
        assertNotNull(id);
        assertNotNull(consumer.dernier);
        assertEquals(id.value(), consumer.dernier.instantaneId());
    }

    @Test
    void rejeter_double_capture_meme_mois_r1() {
        InstantaneMensuelService service = nouveauService();
        service.capturer(new InstantaneMensuelUseCases.CapturerInstantaneCommande(
                CENTRE, Mois.de(2026, 3), "{}"), MAINTENANT);
        assertThrows(IllegalArgumentException.class, () ->
                service.capturer(new InstantaneMensuelUseCases.CapturerInstantaneCommande(
                        CENTRE, Mois.de(2026, 3), "{}"), MAINTENANT));
    }

    @Test
    void consulter_inexistant_lance_exception() {
        InstantaneMensuelService service = nouveauService();
        assertThrows(IllegalArgumentException.class, () ->
                service.consulter(new InstantaneMensuelId(UUID.randomUUID()), CENTRE));
    }

    @Test
    void lister_par_centre_pagine() {
        InstantaneMensuelService service = nouveauService();
        PagedResult<InstantaneMensuel> page = service.listerParCentre(CENTRE, 0, 20);
        assertEquals(0, page.total());
    }

    private static class FakeInstantaneRepository implements InstantaneMensuelRepository {
        private final List<InstantaneMensuel> stock = new ArrayList<>();

        @Override
        public void save(InstantaneMensuel instantane) {
            stock.removeIf(i -> i.id().equals(instantane.id()));
            stock.add(instantane);
        }

        @Override
        public Optional<InstantaneMensuel> trouver(InstantaneMensuelId id, CentreId centreId) {
            return stock.stream()
                    .filter(i -> i.id().equals(id) && i.centreId().equals(centreId))
                    .findFirst();
        }

        @Override
        public boolean existeParCentreEtMois(CentreId centreId, Mois mois) {
            return stock.stream()
                    .anyMatch(i -> i.centreId().equals(centreId) && i.mois().equals(mois));
        }

        @Override
        public PagedResult<InstantaneMensuel> listerParCentre(CentreId centreId, int page, int size) {
            List<InstantaneMensuel> filtres = stock.stream()
                    .filter(i -> i.centreId().equals(centreId)).toList();
            int from = Math.min(page * size, filtres.size());
            int to = Math.min(from + size, filtres.size());
            return PagedResult.of(filtres.subList(from, to), filtres.size(), page, size);
        }
    }

    private static class CapturingEventConsumer implements Consumer<InstantaneMensuelCapture> {
        private InstantaneMensuelCapture dernier;

        @Override
        public void accept(InstantaneMensuelCapture evenement) {
            this.dernier = evenement;
        }
    }
}
