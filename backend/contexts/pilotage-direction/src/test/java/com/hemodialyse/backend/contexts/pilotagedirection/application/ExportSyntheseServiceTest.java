package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirectionId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirectionRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ExportSynthese;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ExportSyntheseId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ExportSyntheseRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.FormatExport;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuelId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuelRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TableauDeBord;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TableauDeBordRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.ExportSyntheseGenere;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExportSyntheseServiceTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final Instant MAINTENANT = Instant.parse("2026-03-10T08:00:00Z");

    private ExportSyntheseService nouveauService() {
        return new ExportSyntheseService(new FakeExportRepository(), new FakeTableauRepository(),
                new FakeAlerteRepository(), new FakeInstantaneRepository(),
                () -> UUID.fromString("99997300-0000-0000-0000-000000000200"),
                evenement -> {
                });
    }

    @Test
    void generer_export_assemble_contenu_des_read_models() {
        ExportSyntheseService service = nouveauService();
        ExportSyntheseId id = service.generer(
                new ExportSyntheseUseCases.GenererExportCommande(CENTRE, "2026-03", FormatExport.HTML),
                MAINTENANT);
        assertNotNull(id);
        ExportSynthese relu = service.telecharger(id, CENTRE);
        assertEquals("2026-03", relu.periode());
    }

    @Test
    void rejeter_commande_nulle() {
        ExportSyntheseService service = nouveauService();
        assertThrows(IllegalArgumentException.class, () -> service.generer(null, MAINTENANT));
    }

    @Test
    void telecharger_inexistant_lance_exception() {
        ExportSyntheseService service = nouveauService();
        assertThrows(IllegalArgumentException.class, () ->
                service.telecharger(new ExportSyntheseId(UUID.randomUUID()), CENTRE));
    }

    private static class FakeExportRepository implements ExportSyntheseRepository {
        private final Map<ExportSyntheseId, ExportSynthese> stock = new HashMap<>();

        @Override
        public void save(ExportSynthese export) {
            stock.put(export.id(), export);
        }

        @Override
        public Optional<ExportSynthese> trouver(ExportSyntheseId id, CentreId centreId) {
            return Optional.ofNullable(stock.get(id))
                    .filter(e -> e.centreId().equals(centreId));
        }
    }

    private static class FakeTableauRepository implements TableauDeBordRepository {
        @Override
        public void save(TableauDeBord tableau) {
        }

        @Override
        public Optional<TableauDeBord> trouver(CentreId centreId) {
            return Optional.empty();
        }
    }

    private static class FakeAlerteRepository implements AlerteDirectionRepository {
        @Override
        public void save(AlerteDirection alerte) {
        }

        @Override
        public Optional<AlerteDirection> trouver(AlerteDirectionId id, CentreId centreId) {
            return Optional.empty();
        }

        @Override
        public boolean existeParCentreEtJour(CentreId centreId, LocalDate date) {
            return false;
        }

        @Override
        public PagedResult<AlerteDirection> listerParCentre(CentreId centreId, int page, int size) {
            return PagedResult.of(List.of(), 0, page, size);
        }
    }

    private static class FakeInstantaneRepository implements InstantaneMensuelRepository {
        @Override
        public void save(InstantaneMensuel instantane) {
        }

        @Override
        public Optional<InstantaneMensuel> trouver(InstantaneMensuelId id, CentreId centreId) {
            return Optional.empty();
        }

        @Override
        public boolean existeParCentreEtMois(CentreId centreId,
                                             com.hemodialyse.backend.contexts.sharedkernel.domain.Mois mois) {
            return false;
        }

        @Override
        public PagedResult<InstantaneMensuel> listerParCentre(CentreId centreId, int page, int size) {
            return PagedResult.of(List.of(), 0, page, size);
        }
    }
}
