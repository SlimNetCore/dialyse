package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLeStockDesGroupes.GroupeStock;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLeStockDesGroupes.StockGroupesOverview;
import com.hemodialyse.backend.contexts.pilotagedirection.application.StockDesGroupes.GroupeDArticles;
import com.hemodialyse.backend.contexts.pilotagedirection.application.StockDesGroupes.Valorisation;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.CentreDeLaSociete;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RegleDuPilotageViolee;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.SocieteSuivie;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Repris de la plateforme (DirectionStockGroupesQueryServiceTest) avec un faux Stock en mémoire (PL10b2, ADR 0152). */
class ConsulterLeStockDesGroupesTest {

    private static final UUID SOCIETE = UUID.randomUUID();
    private static final UUID C1 = UUID.randomUUID();
    private static final UUID C2 = UUID.randomUUID();

    private final HorlogeDuPilotage horloge = HorlogeDuPilotage.auFuseauDeReference(
            Clock.fixed(Instant.parse("2026-11-11T23:30:00Z"), ZoneId.of("UTC")), ZoneId.of("Africa/Algiers"));
    private final SocietesSuivies societes = id -> new SocieteSuivie(SOCIETE, "Société",
            List.of(new CentreDeLaSociete(C1, "Annaba", true), new CentreDeLaSociete(C2, "Rouiba", true)));

    /** Faux Stock : les groupes donnés, 1000 DA de stock par article, et la trace des centres demandés. */
    private static final class StockEnMemoire implements StockDesGroupes {
        private final List<GroupeDArticles> groupes;
        private final Set<UUID> centresDemandes = new HashSet<>();
        private final List<UUID> centresValorises = new ArrayList<>();

        private StockEnMemoire(List<GroupeDArticles> groupes) {
            this.groupes = groupes;
        }

        @Override
        public List<GroupeDArticles> groupes(Collection<UUID> centres) {
            centresDemandes.addAll(centres);
            return groupes;
        }

        @Override
        public Map<UUID, Valorisation> valoriser(UUID centre, Set<UUID> articles, OffsetDateTime debut, OffsetDateTime finExclue) {
            centresValorises.add(centre);
            BigDecimal mille = new BigDecimal("1000");
            Valorisation v = new Valorisation(mille, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, mille);
            Map<UUID, Valorisation> valeurs = new java.util.HashMap<>();
            articles.forEach(a -> valeurs.put(a, v));
            return valeurs;
        }
    }

    @Test
    void deux_centres_qui_nomment_un_groupe_pareil_le_consolident() {
        StockEnMemoire stock = new StockEnMemoire(List.of(
                new GroupeDArticles(UUID.randomUUID(), C1, "Kit CNAS", "kit cnas", Set.of(UUID.randomUUID())),
                new GroupeDArticles(UUID.randomUUID(), C2, "KIT cnas", "kit cnas", Set.of(UUID.randomUUID()))));

        StockGroupesOverview vue = new ConsulterLeStockDesGroupes(societes, stock, horloge)
                .execute(SOCIETE, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        assertEquals(1, vue.groupes().size());
        GroupeStock g = vue.groupes().get(0);
        assertEquals(2, g.nbCentres());
        assertEquals(2, g.nbArticles());
        assertEquals(0, new BigDecimal("2000").compareTo(g.total().valeurFin()));
        assertEquals(0, new BigDecimal("2000").compareTo(g.total().valeurDebut()));
        assertEquals(2, g.centres().size());
    }

    @Test
    void seuls_les_groupes_des_centres_de_la_societe_sont_lus() {
        StockEnMemoire stock = new StockEnMemoire(List.of());

        StockGroupesOverview vue = new ConsulterLeStockDesGroupes(societes, stock, horloge).execute(SOCIETE, null, null);

        assertEquals(Set.of(C1, C2), stock.centresDemandes);
        assertTrue(vue.groupes().isEmpty());
        assertTrue(stock.centresValorises.isEmpty(), "aucun groupe : rien à valoriser");
    }

    @Test
    void la_fin_par_defaut_est_le_jour_d_alger() {
        StockGroupesOverview vue = new ConsulterLeStockDesGroupes(societes, new StockEnMemoire(List.of()), horloge).execute(SOCIETE, null, null);

        assertEquals(LocalDate.of(2026, 11, 12), vue.to());
        assertEquals(LocalDate.of(2026, 1, 1), vue.from());
    }

    @Test
    void une_periode_invalide_ou_trop_longue_est_refusee() {
        ConsulterLeStockDesGroupes cas = new ConsulterLeStockDesGroupes(societes, new StockEnMemoire(List.of()), horloge);
        assertEquals("PERIODE_INVALIDE", assertThrows(RegleDuPilotageViolee.class,
                () -> cas.execute(SOCIETE, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 1))).code());
        assertEquals("PERIODE_TROP_LONGUE", assertThrows(RegleDuPilotageViolee.class,
                () -> cas.execute(SOCIETE, LocalDate.of(2015, 1, 1), LocalDate.of(2026, 2, 1))).code());
    }
}
