package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.contrat.CentresFournisSurJdbcDeTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Caractérisation O4 lot B : ce que le Stock tire de la table des centres hors horloge et centres actifs. Écrit et vert AVANT la
 * migration (lecteurs SQL), puis exécuté à l'identique après (seule la construction de l'objet testé change).
 * <ul>
 *   <li>la liste des centres du langage publié des opérations de stock : TOUS les centres (désactivés et sans état compris) ;</li>
 *   <li>le verrou de période d'un inventaire : sans effet visible pour un centre connu comme pour un centre inconnu.</li>
 * </ul>
 */
class LecturesDesCentresDuStockTest {

    private static final UUID ACTIF = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID DESACTIVE = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID SANS_ETAT = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;

    @BeforeEach
    void preparer() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        transactions = new DataSourceTransactionManager(source);
        jdbc.execute("CREATE TABLE centers (id UUID PRIMARY KEY, actif BOOLEAN)");
        jdbc.update("INSERT INTO centers (id, actif) VALUES (?, TRUE)", ACTIF);
        jdbc.update("INSERT INTO centers (id, actif) VALUES (?, FALSE)", DESACTIVE);
        jdbc.update("INSERT INTO centers (id, actif) VALUES (?, NULL)", SANS_ETAT);
    }

    @Test
    void les_operations_de_stock_listent_tous_les_centres_desactives_et_sans_etat_compris() {
        List<UUID> centres = operationsDeStock().centres();
        assertThat(centres).containsExactlyInAnyOrder(ACTIF, DESACTIVE, SANS_ETAT);
    }

    @Test
    void le_verrou_de_periode_d_un_inventaire_ne_leve_rien_pour_un_centre_connu_ou_inconnu() {
        var inventaires = inventaires();
        new TransactionTemplate(transactions).executeWithoutResult(statut -> {
            assertThatCode(() -> inventaires.verrouillerLaPeriode(new CentreId(ACTIF))).doesNotThrowAnyException();
            assertThatCode(() -> inventaires.verrouillerLaPeriode(new CentreId(UUID.randomUUID()))).doesNotThrowAnyException();
        });
    }

    private com.hemodialyse.backend.contexts.stock.publishedlanguage.OperationsDeStock operationsDeStock() {
        return new OperationsDeStockJdbc(jdbc, new CentresFournisSurJdbcDeTest(jdbc));
    }

    private com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaires inventaires() {
        return new InventairesJdbc(jdbc, new TransactionTemplate(transactions), new CentresFournisSurJdbcDeTest(jdbc));
    }
}
