package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.NumerotationDesDocumentsJdbc;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.NumerotationDesDocuments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contrat de la numérotation des documents sur H2 (jeu partagé avec le double) + atomicité : un refus n'en consomme aucun. */
class NumerotationDesDocumentsJdbcContratTest extends NumerotationDesDocumentsContrat {

    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;

    @BeforeEach
    void base_vide() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:numerotation-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE app_settings (center_id UUID NOT NULL, cle VARCHAR(50) NOT NULL, "
                + "dernier_compteur BIGINT NOT NULL, prefixe VARCHAR(20) NOT NULL, PRIMARY KEY (center_id, cle))");
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    @Override
    protected NumerotationDesDocuments numerotation() {
        return new NumerotationDesDocumentsJdbc(jdbc);
    }

    @Override
    protected void precharger(UUID centre, String type, String prefixe, long compteur) {
        jdbc.update("INSERT INTO app_settings (center_id, cle, prefixe, dernier_compteur) VALUES (?, ?, ?, ?)", centre, type, prefixe,
                compteur);
    }

    @Test
    void un_refus_du_document_annule_l_increment_et_ne_consomme_aucun_numero() {
        var numerotation = numerotation();
        assertThat(numerotation.suivant(CENTRE_A, "SEQ_BC", "BC-")).isEqualTo("BC-00001");

        assertThatThrownBy(() -> transactions.executeWithoutResult(statut -> {
            assertThat(numerotation.suivant(CENTRE_A, "SEQ_BC", "BC-")).isEqualTo("BC-00002");
            throw new IllegalStateException("refus du document");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(numerotation.suivant(CENTRE_A, "SEQ_BC", "BC-")).isEqualTo("BC-00002");
    }

    @Test
    void deux_reservations_simultanees_sur_un_compteur_existant_recoivent_deux_numeros_distincts() throws Exception {
        precharger(CENTRE_A, "SEQ_BS", "BS-", 10);
        var numerotation = numerotation();
        var depart = new CountDownLatch(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var un = pool.submit(() -> transactions.execute(s -> reserver(numerotation, depart)));
            var deux = pool.submit(() -> transactions.execute(s -> reserver(numerotation, depart)));
            assertThat(Set.of(un.get(10, TimeUnit.SECONDS), deux.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder("BS-00011",
                    "BS-00012");
        }
    }

    private static String reserver(NumerotationDesDocuments numerotation, CountDownLatch depart) {
        depart.countDown();
        try {
            depart.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return numerotation.suivant(CENTRE_A, "SEQ_BS", "BS-");
    }
}
