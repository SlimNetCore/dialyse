package com.hemodialyse.backend.contexts.stock.contrat;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.reception.EcritureDeStockConcurrente;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.EcrituresDuStockJdbc;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.SortiesDuStockJdbc;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contrat PostgreSQL 16 REEL des ecritures du Stock (S5, S6) : verrou de ligne {@code FOR UPDATE} avec delai de requete (409 au
 * lieu d'une attente indefinie), receptions croisees sans interblocage, sorties simultanees d'un meme lot sans survente. Base
 * JETABLE uniquement : {@code -Dstock.pg16.url=jdbc:postgresql://...} (schema Flyway deja applique).
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
class StockPostgresContractIT {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99996800-0000-0000-0000-00000000000a"));

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactions;

    private UUID a1;
    private UUID a2;

    @AfterEach
    void vider() {
        jdbc.update("DELETE FROM lots WHERE center_id = ?", CENTRE.valeur());
        jdbc.update("DELETE FROM articles WHERE center_id = ?", CENTRE.valeur());
    }

    @BeforeEach
    void preparer() {
        vider();
        a1 = article("PG1");
        a2 = article("PG2");
    }

    private UUID article(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO articles (id, active, center_id, code, gere_par_lot, libelle, seuil_alerte, stock_quantity, unite, "
                        + "created_at) VALUES (?, true, ?, ?, false, ?, 0, 0, 'piece', ?)", id, CENTRE.valeur(), code, "Article " + code,
                OffsetDateTime.now());
        return id;
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactions);
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
            java.util.ArrayList<T> resultats = new java.util.ArrayList<>();
            for (Future<T> f : futurs) {
                resultats.add(f.get(60, TimeUnit.SECONDS));
            }
            return resultats;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void une_ecriture_qui_tient_le_verrou_donne_une_ecriture_concurrente_apres_le_delai() throws Exception {
        EcrituresDuStockJdbc ecritures = new EcrituresDuStockJdbc(jdbc);
        CountDownLatch verrouPris = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> detenteur = pool.submit(() -> transaction().executeWithoutResult(statut -> {
                jdbc.queryForList("SELECT id FROM articles WHERE id = ? FOR UPDATE", UUID.class, a1);
                verrouPris.countDown();
                try {
                    liberer.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            verrouPris.await();
            long debut = System.nanoTime();
            Throwable erreur = assertThrows(Throwable.class, () -> transaction().executeWithoutResult(
                    statut -> ecritures.verrouiller(CENTRE, List.of(a1))));
            long secondes = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - debut);
            liberer.countDown();
            detenteur.get();
            assertInstanceOf(EcritureDeStockConcurrente.class, erreur);
            assertTrue(secondes >= 4 && secondes <= 20, "delai observe : " + secondes + " s");
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void deux_verrouillages_croises_ne_se_bloquent_pas() throws Exception {
        EcrituresDuStockJdbc ecritures = new EcrituresDuStockJdbc(jdbc);
        CountDownLatch alignes = new CountDownLatch(2);
        List<Boolean> resultats = enParallele(List.of(() -> croise(ecritures, alignes, List.of(a1, a2)),
                () -> croise(ecritures, alignes, List.of(a2, a1))));
        assertEquals(List.of(true, true), resultats);
    }

    private boolean croise(EcrituresDuStockJdbc ecritures, CountDownLatch alignes, List<UUID> articles) {
        return Boolean.TRUE.equals(transaction().execute(statut -> {
            ecritures.verrouiller(CENTRE, articles);
            alignes.countDown();
            try {
                alignes.await(2, TimeUnit.SECONDS);
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true;
        }));
    }

    @Test
    void deux_sorties_simultanees_d_un_meme_lot_ne_vendent_pas_plus_que_le_stock() throws Exception {
        UUID lot = UUID.randomUUID();
        jdbc.update("INSERT INTO lots (id, article_id, center_id, created_at, numero_lot, quantite_initiale, quantite_restante) "
                + "VALUES (?, ?, ?, ?, 'PGL', 10, 10)", lot, a1, CENTRE.valeur(), OffsetDateTime.now());
        EcrituresDuStockJdbc ecritures = new EcrituresDuStockJdbc(jdbc);
        SortiesDuStockJdbc sorties = new SortiesDuStockJdbc(jdbc);
        Callable<Boolean> sortie = () -> Boolean.TRUE.equals(transaction().execute(statut -> {
            ecritures.verrouiller(CENTRE, List.of(a1));
            var courant = sorties.lot(CENTRE, lot).orElseThrow();
            if (courant.quantiteRestante().compareTo(new BigDecimal("6")) < 0) {
                return false;
            }
            sorties.consommer(CENTRE, lot, new BigDecimal("6"));
            return true;
        }));
        List<Boolean> resultats = enParallele(List.of(sortie, sortie));
        assertEquals(1, resultats.stream().filter(r -> r).count());
        assertEquals(0, new BigDecimal("4").compareTo(sorties.lot(CENTRE, lot).orElseThrow().quantiteRestante()));
    }
}

