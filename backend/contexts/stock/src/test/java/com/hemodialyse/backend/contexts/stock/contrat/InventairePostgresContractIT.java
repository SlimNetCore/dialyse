package com.hemodialyse.backend.contexts.stock.contrat;

import com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.stock.application.inventaire.GestionDesInventaires;
import com.hemodialyse.backend.contexts.stock.application.reception.RejeuDuPmp;
import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaire;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.LigneDInventaire;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.StatutDInventaire;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.ArticlesJdbc;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.EcrituresDuStockJdbc;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.InventairesJdbc;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.LecturesDuStockJdbc;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.SortiesPourSeancesJdbc;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.StockInventorieJdbc;
import com.hemodialyse.backend.contexts.stock.infrastructure.transaction.UniteDeTravailSpring;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrat PostgreSQL 16 REEL de l'inventaire (S7) : les adaptateurs JDBC et les verrous sur une vraie base — cycle ouverture, comptage,
 * clôture (FEFO avec date absente en dernier, mouvements figés, stock de départ, PMP rejoué) ; deux ouvertures simultanées sérialisées
 * par le verrou du centre ; une ouverture qui attend une écriture de stock en cours. Base JETABLE uniquement :
 * {@code -Dstock.pg16.url=jdbc:postgresql://...} (schéma Flyway déjà appliqué).
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
class InventairePostgresContractIT {

    private static final UUID ID_CENTRE = UUID.fromString("99996800-0000-0000-0000-0000000000c1");
    private static final CentreId CENTRE = new CentreId(ID_CENTRE);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactions;

    private UUID a1;
    private UUID a2;
    private UUID lotTot;
    private UUID lotSansDate;
    private GestionDesInventaires gestion;

    private static OffsetDateTime minuit(int moinsN) {
        return LocalDate.now(ZoneOffset.UTC).minusDays(moinsN).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactions);
    }

    @AfterEach
    void vider() {
        jdbc.update("DELETE FROM inventaire_lignes WHERE center_id = ?", ID_CENTRE);
        jdbc.update("DELETE FROM inventaires WHERE center_id = ?", ID_CENTRE);
        jdbc.update("DELETE FROM stock_movements WHERE center_id = ?", ID_CENTRE);
        jdbc.update("DELETE FROM lots WHERE center_id = ?", ID_CENTRE);
        jdbc.update("DELETE FROM articles WHERE center_id = ?", ID_CENTRE);
        jdbc.update("DELETE FROM app_settings WHERE center_id = ?", ID_CENTRE);
        jdbc.update("DELETE FROM centers WHERE id = ?", ID_CENTRE);
    }

    @BeforeEach
    void preparer() {
        vider();
        jdbc.update("INSERT INTO centers (id, actif, code, name, societe_id) VALUES (?, true, 'PGINV', 'Centre PG inventaire', ?)", ID_CENTRE,
                UUID.randomUUID());
        a1 = article("PGI1", "Article 1", "8", "5");
        a2 = article("PGI2", "Article 2", "0", "0");
        lotTot = lot(a1, "L-TOT", LocalDate.now(ZoneOffset.UTC).plusDays(10), "3");
        lotSansDate = lot(a1, "L-SANS-DATE", null, "5");
        EcrituresDuStockJdbc ecritures = new EcrituresDuStockJdbc(jdbc);
        TransactionTemplate tt = transaction();
        gestion = new GestionDesInventaires(new InventairesJdbc(jdbc, tt), new StockInventorieJdbc(jdbc), new ArticlesJdbc(jdbc), ecritures,
                new RejeuDuPmp(new LecturesDuStockJdbc(jdbc), ecritures), new SortiesPourSeancesJdbc(jdbc, tt), centre -> false,
                new UniteDeTravailSpring(tt), (centre, statut, reference, date, par) -> {
        }, HorlogeDesCentres.uniforme(Clock.systemUTC()), UUID::randomUUID);
    }

    private UUID article(String code, String libelle, String stock, String pmp) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO articles (id, active, center_id, code, gere_par_lot, libelle, seuil_alerte, stock_quantity, unite, "
                        + "pmp_courant, created_at) VALUES (?, true, ?, ?, false, ?, 0, ?, 'piece', ?, ?)", id, ID_CENTRE, code, libelle,
                new BigDecimal(stock), new BigDecimal(pmp), OffsetDateTime.now());
        return id;
    }

    private UUID lot(UUID article, String numero, LocalDate peremption, String quantite) {
        UUID lot = UUID.randomUUID();
        jdbc.update("INSERT INTO lots (id, article_id, center_id, created_at, numero_lot, date_peremption, quantite_initiale, "
                        + "quantite_restante, pmp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 5)", lot, article, ID_CENTRE, minuit(10), numero,
                peremption, new BigDecimal(quantite), new BigDecimal(quantite));
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, lot_id, mouvement_type, quantite, prix_unitaire, created_by, "
                        + "created_at, saisi_le) VALUES (?, ?, ?, ?, 'ENTREE', ?, 5, 'test', ?, ?)", UUID.randomUUID(), ID_CENTRE, article, lot,
                new BigDecimal(quantite), minuit(10), minuit(10).plusHours(1));
        return lot;
    }

    private LocalDate hier() {
        return LocalDate.now(ZoneOffset.UTC).minusDays(1);
    }

    private <T> List<T> enParallele(List<Callable<T>> taches) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(taches.size());
        CountDownLatch depart = new CountDownLatch(1);
        try {
            List<Future<T>> futurs = taches.stream().map(t -> pool.submit(() -> {
                depart.await();
                return t.call();
            })).toList();
            depart.countDown();
            List<T> resultats = new ArrayList<>();
            for (Future<T> f : futurs) {
                resultats.add(f.get(60, TimeUnit.SECONDS));
            }
            return resultats;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void le_cycle_ouverture_comptage_cloture_fonctionne_sur_postgresql() {
        Inventaire ouvert = gestion.ouvrir(CENTRE, hier(), "annuel", "pharmacien");
        assertEquals("INV-00001", ouvert.reference());
        assertEquals(List.of("L-TOT", "L-SANS-DATE"), ouvert.lignes().stream().map(LigneDInventaire::numeroLot).toList(),
                "FEFO : la date de péremption absente en dernier");
        assertTrue(gestion.etat(CENTRE).mouvementsBloques());

        Inventaire relu = gestion.consulter(CENTRE, ouvert.id());
        assertEquals(2, relu.lignes().size());
        LigneDInventaire tot = relu.lignes().get(0);
        Inventaire compte = gestion.compter(CENTRE, ouvert.id(), tot.id(), new BigDecimal("2"), "CASSE", "inf");
        compte = gestion.compter(CENTRE, ouvert.id(), compte.lignes().get(1).id(), new BigDecimal("5"), null, "inf");

        Inventaire clos = gestion.cloturer(CENTRE, ouvert.id(), "pharmacien");
        assertEquals(StatutDInventaire.CLOTURE, clos.statut());
        assertEquals(0, new BigDecimal("2").compareTo(jdbc.queryForObject("SELECT quantite_restante FROM lots WHERE id = ?", BigDecimal.class,
                lotTot)));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE center_id = ? AND mouvement_type = 'INVENTAIRE'",
                Integer.class, ID_CENTRE));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE center_id = ? AND mouvement_type = 'ENTREE' "
                + "AND inventaire_id IS NOT NULL", Integer.class, ID_CENTRE), "les entrées de l'article inventorié sont figées");
        assertEquals(0, new BigDecimal("7").compareTo(jdbc.queryForObject("SELECT stock_quantity FROM articles WHERE id = ?", BigDecimal.class,
                a1)), "le stock de l'article est rejoué depuis le stock de départ");

        assertEquals(hier(), gestion.etat(CENTRE).derniereCloture());
        assertFalse(gestion.etat(CENTRE).mouvementsBloques());
        var liste = gestion.lister(CENTRE, 0, 20);
        assertEquals(1, liste.total());
        assertEquals(0, new BigDecimal("-5.00").compareTo(liste.elements().get(0).valeurEcarts()));
        assertEquals(1, liste.elements().get(0).ecarts());
    }

    @Test
    void deux_ouvertures_simultanees_sont_serialisees_par_le_verrou_du_centre() throws Exception {
        Callable<String> ouverture = () -> {
            try {
                gestion.ouvrir(CENTRE, hier(), null, "p");
                return "OUVERT";
            } catch (RefusDuReferentiel refus) {
                return refus.code();
            }
        };
        List<String> resultats = enParallele(List.of(ouverture, ouverture));
        assertEquals(1, resultats.stream().filter("OUVERT"::equals).count(), resultats.toString());
        assertEquals(1, resultats.stream().filter("INVENTORY_ALREADY_IN_PROGRESS"::equals).count(), resultats.toString());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inventaires WHERE center_id = ?", Integer.class, ID_CENTRE));
        assertEquals("INV-00001", jdbc.queryForObject("SELECT reference FROM inventaires WHERE center_id = ?", String.class, ID_CENTRE));
    }

    @Test
    void l_ouverture_attend_une_ecriture_de_stock_en_cours_et_l_inclut_au_theorique() throws Exception {
        CountDownLatch verrouPris = new CountDownLatch(1);
        CountDownLatch terminer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> ecriture = pool.submit(() -> transaction().executeWithoutResult(statut -> {
                jdbc.queryForList("SELECT id FROM articles WHERE id = ? FOR UPDATE", UUID.class, a1);
                verrouPris.countDown();
                try {
                    terminer.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                jdbc.update("INSERT INTO lots (id, article_id, center_id, created_at, numero_lot, date_peremption, quantite_initiale, "
                                + "quantite_restante, pmp) VALUES (?, ?, ?, ?, 'L-RECU', ?, 4, 4, 5)", UUID.randomUUID(), a1, ID_CENTRE, minuit(2),
                        LocalDate.now(ZoneOffset.UTC).plusDays(60));
            }));
            verrouPris.await();
            Future<Inventaire> ouverture = pool.submit(() -> gestion.ouvrir(CENTRE, hier(), null, "p"));
            Thread.sleep(500);
            assertFalse(ouverture.isDone(), "l'ouverture doit attendre l'écriture en cours");
            terminer.countDown();
            ecriture.get(10, TimeUnit.SECONDS);
            Inventaire ouvert = ouverture.get(30, TimeUnit.SECONDS);
            assertTrue(ouvert.lignes().stream().anyMatch(l -> "L-RECU".equals(l.numeroLot())), "le lot reçu avant le gel figure au théorique");
        } finally {
            terminer.countDown();
            pool.shutdownNow();
        }
    }
}

