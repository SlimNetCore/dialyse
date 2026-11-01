package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.TableauDeBord;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TableauDeBordRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TableauDeBordServiceTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final Instant MAINTENANT = Instant.parse("2026-03-10T08:00:00Z");

    @Test
    void actualiser_puis_consulter() {
        TableauDeBordService service = new TableauDeBordService(new FakeTableauRepository());
        TableauDeBord t = service.actualiser(
                new TableauDeBordUseCases.ActualiserTableauDeBordCommande(CENTRE, "{\"seances\":10}"),
                MAINTENANT);
        assertNotNull(t);
        assertEquals("{\"seances\":10}", service.consulter(CENTRE).indicateurs());
    }

    @Test
    void rejeter_commande_nulle() {
        TableauDeBordService service = new TableauDeBordService(new FakeTableauRepository());
        assertThrows(IllegalArgumentException.class, () -> service.actualiser(null, MAINTENANT));
    }

    @Test
    void consulter_inexistant_lance_exception() {
        TableauDeBordService service = new TableauDeBordService(new FakeTableauRepository());
        assertThrows(IllegalArgumentException.class, () -> service.consulter(CENTRE));
    }

    private static class FakeTableauRepository implements TableauDeBordRepository {
        private final Map<CentreId, TableauDeBord> stock = new HashMap<>();

        @Override
        public void save(TableauDeBord tableau) {
            stock.put(tableau.centreId(), tableau);
        }

        @Override
        public Optional<TableauDeBord> trouver(CentreId centreId) {
            return Optional.ofNullable(stock.get(centreId));
        }
    }
}
