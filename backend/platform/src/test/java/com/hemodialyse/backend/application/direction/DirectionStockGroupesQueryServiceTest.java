package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.CentreInfo;
import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.SocieteInfo;
import com.hemodialyse.backend.application.direction.DirectionStockGroupesQueryService.GroupeStock;
import com.hemodialyse.backend.application.direction.DirectionStockGroupesQueryService.StockGroupesOverview;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.stock.model.GroupeArticle;
import com.hemodialyse.backend.domain.stock.port.GroupeArticleRepositoryPort;
import com.hemodialyse.backend.domain.stock.service.ValorisationStockCalculator.Valorisation;
import com.hemodialyse.backend.application.stock.ValorisationArticlesService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DirectionStockGroupesQueryServiceTest {

    private static final UUID SOCIETE = UUID.randomUUID();
    private static final UUID C1 = UUID.randomUUID();
    private static final UUID C2 = UUID.randomUUID();
    private static final OffsetDateTime JAN = OffsetDateTime.of(2026, 1, 10, 8, 0, 0, 0, ZoneOffset.UTC);

    private final DirectionDashboardQueryService dashboard = mock(DirectionDashboardQueryService.class);
    private final GroupeArticleRepositoryPort groupes = mock(GroupeArticleRepositoryPort.class);
    private final ValorisationArticlesService valorisation = mock(ValorisationArticlesService.class);
    private final DirectionStockGroupesQueryService service =
            new DirectionStockGroupesQueryService(dashboard, groupes, valorisation);

    private static Valorisation valeur(String montant) {
        BigDecimal v = new BigDecimal(montant);
        return new Valorisation(v, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, v, BigDecimal.ONE);
    }

    private void societeAvecDeuxCentres() {
        when(dashboard.societe(SOCIETE)).thenReturn(new SocieteInfo(SOCIETE, "Société",
                List.of(new CentreInfo(C1, "Annaba", true), new CentreInfo(C2, "Rouiba", true))));
    }

    @Test
    void should_consolidate_same_named_groups_across_the_centers_of_the_company() {
        societeAvecDeuxCentres();
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        when(groupes.findByCenters(any())).thenReturn(List.of(
                GroupeArticle.creer(C1, "Kit CNAS", null, List.of(a1)),
                GroupeArticle.creer(C2, "KIT  cnas", null, List.of(a2))));
        when(valorisation.valoriser(eq(C1), any(), any(), any())).thenReturn(Map.of(a1, valeur("1000")));
        when(valorisation.valoriser(eq(C2), any(), any(), any())).thenReturn(Map.of(a2, valeur("1000")));

        StockGroupesOverview overview = service.groupes(SOCIETE, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        assertEquals(1, overview.groupes().size());
        GroupeStock g = overview.groupes().get(0);
        assertEquals(2, g.nbCentres());
        assertEquals(2, g.nbArticles());
        assertEquals(0, new BigDecimal("2000").compareTo(g.total().valeurFin()));
        assertEquals(0, new BigDecimal("2000").compareTo(g.total().valeurDebut()));
        assertEquals(2, g.centres().size());
    }

    @Test
    void should_only_read_the_groups_of_the_centers_of_the_company() {
        societeAvecDeuxCentres();
        when(groupes.findByCenters(any())).thenReturn(List.of());

        StockGroupesOverview overview = service.groupes(SOCIETE, null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(groupes).findByCenters(captor.capture());
        assertEquals(Set.of(C1, C2), new HashSet<>(captor.getValue()));
        assertTrue(overview.groupes().isEmpty());
        verifyNoInteractions(valorisation);
    }

    @Test
    void should_reject_an_invalid_period() {
        assertThrows(BusinessException.class,
                () -> service.groupes(SOCIETE, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 1)));
        assertThrows(BusinessException.class,
                () -> service.groupes(SOCIETE, LocalDate.of(2015, 1, 1), LocalDate.of(2026, 2, 1)));
    }
}
