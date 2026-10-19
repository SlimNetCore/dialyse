package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.NumeroDePiece;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.SequenceDesNumerosDePieces;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SequenceDesNumerosDePiecesJdbcTest {

    @Test
    void verrouilleLaSequenceEntreTransactionsConcurrentes() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:sequence-c4;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE app_settings (center_id UUID NOT NULL, cle VARCHAR(50) NOT NULL, "
                + "prefixe VARCHAR(20) NOT NULL, dernier_compteur BIGINT NOT NULL, PRIMARY KEY (center_id, cle))");
        jdbc.execute("CREATE TABLE ecritures_comptables (center_id UUID NOT NULL, journal_code VARCHAR(10) NOT NULL, "
                + "numero_piece VARCHAR(40) NOT NULL)");
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        var sequence = new SequenceDesNumerosDePiecesJdbc(jdbc);
        CentreId centre = new CentreId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        CountDownLatch depart = new CountDownLatch(2);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var premier = pool.submit(() -> attribuer(transactions, sequence, jdbc, centre, depart));
            var second = pool.submit(() -> attribuer(transactions, sequence, jdbc, centre, depart));
            Set<String> numeros = Set.of(premier.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertEquals(2, numeros.size());
            assertEquals(Set.of("AC-2026-000001", "AC-2026-000002"), numeros);
        }
    }

    @Test
    void verrouilleLaSourceEntreTransactionsConcurrentes() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:source-c4;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE app_settings (center_id UUID NOT NULL, cle VARCHAR(50) NOT NULL, "
                + "prefixe VARCHAR(20) NOT NULL, dernier_compteur BIGINT NOT NULL, PRIMARY KEY (center_id, cle))");
        jdbc.execute("CREATE TABLE ecritures_comptables (center_id UUID NOT NULL, source_id UUID NOT NULL)");
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        var sequence = new SequenceDesNumerosDePiecesJdbc(jdbc);
        CentreId centre = new CentreId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        UUID sourceId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        CountDownLatch depart = new CountDownLatch(2);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var premier = pool.submit(() -> insererSourceUneFois(transactions, sequence, jdbc, centre, sourceId, depart));
            var second = pool.submit(() -> insererSourceUneFois(transactions, sequence, jdbc, centre, sourceId, depart));
            int creations = (premier.get(10, TimeUnit.SECONDS) ? 1 : 0) + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);

            assertEquals(1, creations);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ecritures_comptables WHERE center_id = ? AND source_id = ?",
                    Integer.class, centre.valeur(), sourceId));
        }
    }

    private String attribuer(TransactionTemplate transactions, SequenceDesNumerosDePieces sequence,
                             JdbcTemplate jdbc, CentreId centre, CountDownLatch depart) throws Exception {
        depart.countDown();
        assertTrue(depart.await(5, TimeUnit.SECONDS));
        return transactions.execute(status -> {
            List<String> existants = sequence.verrouillerEtLireLesNumeros(centre, CodeJournal.AC, 2026);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            NumeroDePiece numero = NumeroDePiece.suivant(CodeJournal.AC, 2026, existants);
            jdbc.update("INSERT INTO ecritures_comptables (center_id, journal_code, numero_piece) VALUES (?, ?, ?)",
                    centre.valeur(), CodeJournal.AC.valeur(), numero.valeur());
            return numero.valeur();
        });
    }

    private boolean insererSourceUneFois(TransactionTemplate transactions, SequenceDesNumerosDePieces sequence,
                                         JdbcTemplate jdbc, CentreId centre, UUID sourceId,
                                         CountDownLatch depart) throws Exception {
        depart.countDown();
        assertTrue(depart.await(5, TimeUnit.SECONDS));
        return transactions.execute(status -> {
            sequence.verrouillerSource(centre, sourceId);
            Integer existantes = jdbc.queryForObject("SELECT COUNT(*) FROM ecritures_comptables "
                    + "WHERE center_id = ? AND source_id = ?", Integer.class, centre.valeur(), sourceId);
            if (existantes != null && existantes > 0) return false;
            jdbc.update("INSERT INTO ecritures_comptables (center_id, source_id) VALUES (?, ?)", centre.valeur(), sourceId);
            return true;
        });
    }
}
