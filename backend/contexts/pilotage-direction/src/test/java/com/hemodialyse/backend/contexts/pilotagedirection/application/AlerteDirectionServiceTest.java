package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirectionId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirectionRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TypeAlerte;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.AlerteDirectionGeneree;
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

class AlerteDirectionServiceTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final Instant MAINTENANT = Instant.parse("2026-03-10T08:00:00Z");
    private static final LocalDate JOUR = LocalDate.of(2026, 3, 10);

    private AlerteDirectionService nouveauService() {
        return new AlerteDirectionService(new FakeAlerteRepository(),
                () -> UUID.fromString("99997300-0000-0000-0000-000000000200"),
                evenement -> {
                });
    }

    @Test
    void generer_alerte_publie_evenement() {
        CapturingEventConsumer consumer = new CapturingEventConsumer();
        FakeAlerteRepository repo = new FakeAlerteRepository();
        AlerteDirectionService service = new AlerteDirectionService(repo,
                () -> UUID.fromString("99997300-0000-0000-0000-000000000200"), consumer);
        AlerteDirectionId id = service.generer(
                new AlerteDirectionUseCases.GenererAlerteCommande(
                        CENTRE, TypeAlerte.SOUS_EFFECTIF, "ratio<0.8", "Sous-effectif", JOUR),
                MAINTENANT);
        assertNotNull(id);
        assertNotNull(consumer.dernier);
        assertEquals("SOUS_EFFECTIF", consumer.dernier.type());
    }

    @Test
    void rejeter_seconde_alerte_meme_jour_r2() {
        AlerteDirectionService service = nouveauService();
        service.generer(new AlerteDirectionUseCases.GenererAlerteCommande(
                CENTRE, TypeAlerte.SOUS_EFFECTIF, null, null, JOUR), MAINTENANT);
        assertThrows(IllegalArgumentException.class, () ->
                service.generer(new AlerteDirectionUseCases.GenererAlerteCommande(
                        CENTRE, TypeAlerte.SUR_CAPACITE, null, null, JOUR), MAINTENANT));
    }

    @Test
    void acquitter_alerte() {
        AlerteDirectionService service = nouveauService();
        AlerteDirectionId id = service.generer(
                new AlerteDirectionUseCases.GenererAlerteCommande(
                        CENTRE, TypeAlerte.SOUS_EFFECTIF, null, null, JOUR), MAINTENANT);
        AlerteDirection acquittee = service.acquitter(id, CENTRE, MAINTENANT);
        assertTrue(acquittee.lue());
    }

    @Test
    void consulter_inexistant_lance_exception() {
        AlerteDirectionService service = nouveauService();
        assertThrows(IllegalArgumentException.class, () ->
                service.consulter(new AlerteDirectionId(UUID.randomUUID()), CENTRE));
    }

    private static class FakeAlerteRepository implements AlerteDirectionRepository {
        private final List<AlerteDirection> stock = new ArrayList<>();

        @Override
        public void save(AlerteDirection alerte) {
            stock.removeIf(a -> a.id().equals(alerte.id()));
            stock.add(alerte);
        }

        @Override
        public Optional<AlerteDirection> trouver(AlerteDirectionId id, CentreId centreId) {
            return stock.stream()
                    .filter(a -> a.id().equals(id) && a.centreId().equals(centreId))
                    .findFirst();
        }

        @Override
        public boolean existeParCentreEtJour(CentreId centreId, LocalDate date) {
            return stock.stream()
                    .anyMatch(a -> a.centreId().equals(centreId) && a.date().equals(date));
        }

        @Override
        public PagedResult<AlerteDirection> listerParCentre(CentreId centreId, int page, int size) {
            List<AlerteDirection> filtres = stock.stream()
                    .filter(a -> a.centreId().equals(centreId)).toList();
            int from = Math.min(page * size, filtres.size());
            int to = Math.min(from + size, filtres.size());
            return PagedResult.of(filtres.subList(from, to), filtres.size(), page, size);
        }
    }

    private static class CapturingEventConsumer implements Consumer<AlerteDirectionGeneree> {
        private AlerteDirectionGeneree dernier;

        @Override
        public void accept(AlerteDirectionGeneree evenement) {
            this.dernier = evenement;
        }
    }
}
