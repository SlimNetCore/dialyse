package com.hemodialyse.backend.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.AnnuaireDesCentresJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.VerrouDuCentreJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.infrastructure.acl.centres.CentresActifsFournis;
import com.hemodialyse.backend.contexts.stock.infrastructure.acl.centres.HorlogeDesCentresFournie;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.InventairesJdbc;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.OperationsDeStockJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Parité du Stock après O4 lot B (ADR 0108) : les adapters du Stock câblés sur les VRAIS traducteurs de la plateforme et sur le VRAI langage
 * publié d'Organisation (H2, mode PostgreSQL) rendent ce que rendaient les lecteurs SQL retirés. Les scénarios sont ceux de la caractérisation
 * écrite et verte sur les lecteurs d'origine : centre actif au sens strict (état non renseigné exclu, KB-ORG-09), tous les centres, jour du
 * fuseau (UTC si absent, blanc, illisible ou centre inconnu), verrou de période. L'horloge est fixe.
 */
class CentresDuStockParOrganisationTest {

    /** 23 h 30 UTC : déjà le lendemain à Alger, encore la veille à Montréal. */
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-10-23T23:30:00Z"), ZoneOffset.UTC);

    private static final UUID ACTIF = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID DESACTIVE = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID SANS_ETAT = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private CentresDuStockParOrganisation centres;

    @BeforeEach
    void preparer() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        transactions = new DataSourceTransactionManager(source);
        jdbc.execute("CREATE TABLE centers (id UUID PRIMARY KEY, actif BOOLEAN, fuseau_horaire VARCHAR(50))");
        jdbc.update("INSERT INTO centers (id, actif) VALUES (?, TRUE)", ACTIF);
        jdbc.update("INSERT INTO centers (id, actif) VALUES (?, FALSE)", DESACTIVE);
        jdbc.update("INSERT INTO centers (id, actif) VALUES (?, NULL)", SANS_ETAT);
        centres = new CentresDuStockParOrganisation(new AnnuaireDesCentresJdbc(jdbc));
    }

    private CentreId centre(String fuseau) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO centers (id, actif, fuseau_horaire) VALUES (?, TRUE, ?)", id, fuseau);
        return new CentreId(id);
    }

    @Test
    void le_stock_exclut_un_centre_dont_l_etat_n_est_pas_renseigne_et_garde_l_ordre_des_identifiants() {
        jdbc.update("DELETE FROM centers WHERE id = ?", DESACTIVE);
        assertThat(new CentresActifsFournis(centres).liste()).containsExactly(new CentreId(ACTIF));
    }

    @Test
    void les_operations_de_stock_listent_tous_les_centres() {
        var operations = new OperationsDeStockJdbc(jdbc, centres);
        assertThat(operations.centres()).containsExactlyInAnyOrder(ACTIF, DESACTIVE, SANS_ETAT);
    }

    @Test
    void le_jour_d_un_centre_est_celui_de_son_fuseau_et_utc_a_defaut() {
        var horloge = new HorlogeDesCentresFournie(centres, HORLOGE);
        assertThat(horloge.aujourdHui(centre("Africa/Algiers"))).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(horloge.aujourdHui(centre("America/Montreal"))).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(horloge.aujourdHui(centre(null))).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(horloge.aujourdHui(centre("  "))).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(horloge.aujourdHui(centre("Nulle/Part"))).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(horloge.aujourdHui(new CentreId(UUID.randomUUID()))).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(horloge.maintenant().toInstant()).isEqualTo(Instant.parse("2026-10-23T23:30:00Z"));
    }

    @Test
    void le_verrou_de_periode_d_un_inventaire_passe_par_organisation_pour_un_centre_connu_ou_inconnu() {
        var inventaires = new InventairesJdbc(jdbc, new TransactionTemplate(transactions),
                new VerrouDuCentreDuStockParOrganisation(new VerrouDuCentreJdbc(jdbc)));
        new TransactionTemplate(transactions).executeWithoutResult(statut -> {
            assertThatCode(() -> inventaires.verrouillerLaPeriode(new CentreId(ACTIF))).doesNotThrowAnyException();
            assertThatCode(() -> inventaires.verrouillerLaPeriode(new CentreId(UUID.randomUUID()))).doesNotThrowAnyException();
        });
    }
}
