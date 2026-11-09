package com.hemodialyse.backend.contexts.stock.contrat;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.stock.application.peremptions.ControlerLesPeremptions;
import com.hemodialyse.backend.contexts.stock.application.peremptions.ControlerLesPeremptions.ResultatDuCentre;
import com.hemodialyse.backend.contexts.stock.infrastructure.acl.centres.CentresActifsFournis;
import com.hemodialyse.backend.contexts.stock.infrastructure.acl.centres.HorlogeDesCentresFournie;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.ArticlesJdbc;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.LecturesDuStockJdbc;
import com.hemodialyse.backend.contexts.stock.infrastructure.transaction.UniteDeTravailSpring;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.LotProcheExpiration;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.NotificationsDesPeremptions.LotSignale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrat PostgreSQL 16 REEL du contrôle des péremptions (S8) : les vrais adaptateurs {@code CentresActifsLegacy},
 * {@code LecturesDuStockJdbc}, {@code ArticlesJdbc}, {@code HorlogeDesCentresLegacy} et {@code UniteDeTravailSpring} (le fake en
 * mémoire et le vrai adaptateur satisfont les mêmes attentes que {@code ControlerLesPeremptionsTest}) : centres actifs seulement,
 * fenêtre J+30 incluse, lots épuisés et sans date exclus, articles inactifs exclus, rappel hebdomadaire des périmés. Base JETABLE
 * uniquement : {@code -Dstock.pg16.url=jdbc:postgresql://...} (schéma Flyway déjà appliqué).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${stock.pg16.url}",
        "spring.datasource.username=${stock.pg16.user:stock_test}",
        "spring.datasource.password=${stock.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "stock.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PeremptionsPostgresContractIT {

    private static final UUID ACTIF = UUID.fromString("99996800-0000-0000-0000-0000000000d1");
    /** Horloge fixe : le jour du centre (Alger, UTC+1) est le 24/10/2026, sans dépendance à la date réelle ni à minuit. */
    private static final Clock FIXE = Clock.fixed(java.time.Instant.parse("2026-10-24T10:00:00Z"), ZoneOffset.UTC);
    private static final UUID INACTIF = UUID.fromString("99996800-0000-0000-0000-0000000000d2");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactions;

    private final List<LotProcheExpiration> publies = new ArrayList<>();
    private final List<List<LotSignale>> notifies = new ArrayList<>();
    private ControlerLesPeremptions controle;

    @AfterEach
    void vider() {
        for (UUID c : List.of(ACTIF, INACTIF)) {
            jdbc.update("DELETE FROM lots WHERE center_id = ?", c);
            jdbc.update("DELETE FROM articles WHERE center_id = ?", c);
            jdbc.update("DELETE FROM centers WHERE id = ?", c);
        }
    }

    @BeforeEach
    void preparer() {
        vider();
        jdbc.update("INSERT INTO centers (id, actif, code, name, societe_id) VALUES (?, true, 'PGPER-A', 'Centre PG péremptions A', ?)",
                ACTIF, UUID.randomUUID());
        jdbc.update("INSERT INTO centers (id, actif, code, name, societe_id) VALUES (?, false, 'PGPER-I', 'Centre PG péremptions I', ?)",
                INACTIF, UUID.randomUUID());
        jdbc.update("UPDATE centers SET fuseau_horaire = 'Africa/Algiers' WHERE id = ?", ACTIF);
        CentresFournisSurJdbcDeTest centres = new CentresFournisSurJdbcDeTest(jdbc);
        controle = new ControlerLesPeremptions(new CentresActifsFournis(centres),
                new HorlogeDesCentresFournie(centres, FIXE), new LecturesDuStockJdbc(jdbc), new ArticlesJdbc(jdbc),
                new UniteDeTravailSpring(new TransactionTemplate(transactions)), publies::add, (centre, jour, lots) -> {
            notifies.add(lots);
            return true;
        });
    }

    private UUID article(UUID centre, String code, boolean actif) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO articles (id, active, center_id, code, gere_par_lot, libelle, seuil_alerte, stock_quantity, unite, "
                + "pmp_courant, created_at) VALUES (?, ?, ?, ?, false, ?, 0, 10, 'piece', 5, ?)", id, actif, centre, code,
                "Article " + code, OffsetDateTime.now());
        return id;
    }

    private void lot(UUID centre, UUID article, String numero, LocalDate peremption, String quantite) {
        jdbc.update("INSERT INTO lots (id, article_id, center_id, created_at, numero_lot, date_peremption, quantite_initiale, "
                        + "quantite_restante, pmp) VALUES (?, ?, ?, ?, ?, ?, 100, ?, 5)", UUID.randomUUID(), article, centre,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(200), numero, peremption, new BigDecimal(quantite));
    }

    @Test
    void les_lots_proches_des_centres_actifs_sont_publies_et_notifies_sur_la_vraie_base() {
        HorlogeDesCentres horloge = new HorlogeDesCentresFournie(new CentresFournisSurJdbcDeTest(jdbc), FIXE);
        LocalDate jour = horloge.aujourdHui(new com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId(ACTIF));
        UUID a = article(ACTIF, "PGPER1", true);
        UUID inactif = article(ACTIF, "PGPER2", false);
        lot(ACTIF, a, "L-30", jour.plusDays(30), "7");
        lot(ACTIF, a, "L-31", jour.plusDays(31), "8");
        lot(ACTIF, a, "L-HIER", jour.minusDays(1), "1");
        lot(ACTIF, a, "L-PERIME-3", jour.minusDays(3), "1");
        lot(ACTIF, a, "L-VIDE", jour.plusDays(5), "0");
        lot(ACTIF, a, "L-SANS-DATE", null, "5");
        lot(ACTIF, inactif, "L-ART-INACTIF", jour.plusDays(5), "2");
        UUID b = article(INACTIF, "PGPER3", true);
        lot(INACTIF, b, "L-CENTRE-INACTIF", jour.plusDays(5), "11");

        List<ResultatDuCentre> resultats = controle.controler().stream()
                .filter(r -> r.centre().valeur().equals(ACTIF) || r.centre().valeur().equals(INACTIF)).toList();

        assertEquals(1, resultats.size(), "le centre inactif n'est pas parcouru");
        assertEquals(2, resultats.get(0).lots());
        assertTrue(resultats.get(0).notifie());
        assertEquals(List.of("L-HIER", "L-30"), publies.stream().filter(e -> e.centre().equals(ACTIF))
                .map(LotProcheExpiration::numeroLot).toList());
        assertEquals(1, notifies.stream().filter(l -> l.stream().anyMatch(s -> s.numeroLot().equals("L-30"))).count());
    }
}
