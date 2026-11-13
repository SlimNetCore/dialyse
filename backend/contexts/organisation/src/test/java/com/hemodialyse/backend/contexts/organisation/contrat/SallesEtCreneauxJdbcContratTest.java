package com.hemodialyse.backend.contexts.organisation.contrat;

import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.IsolementDesSallesJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.SallesEtCreneauxDuCentreJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.VerrouDuCentreJdbc;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.DefinirLesSallesDIsolement;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.SallesEtCreneauxDuCentre;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'adapter JDBC des salles et des créneaux passe le même contrat que le faux en mémoire (H2, mode PostgreSQL), plus ce qui n'a de sens
 * qu'avec une base : la commande d'isolement suit la transaction de l'appelant, et la réservation du centre ne crée aucune ligne.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:organisation-salles-creneaux;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SallesEtCreneauxJdbcContratTest extends SallesEtCreneauxContrat {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void creerLesTablesEtVider() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS centers (id UUID PRIMARY KEY)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS salle (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(50) NOT NULL, "
                + "nom VARCHAR(255) NOT NULL, isolement VARCHAR(3) NOT NULL DEFAULT 'NON', capacite INTEGER)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS position_creneau (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "code VARCHAR(50) NOT NULL, libelle VARCHAR(255) NOT NULL, actif BOOLEAN NOT NULL DEFAULT TRUE)");
        jdbc.update("DELETE FROM salle");
        jdbc.update("DELETE FROM position_creneau");
        jdbc.update("DELETE FROM centers");
    }

    @Override
    protected SallesEtCreneauxDuCentre lectures() {
        return new SallesEtCreneauxDuCentreJdbc(jdbc);
    }

    @Override
    protected DefinirLesSallesDIsolement commande() {
        return new IsolementDesSallesJdbc(jdbc);
    }

    @Override
    protected void salle(UUID centre, UUID id, String code, String nom, boolean isolement, Integer capacite) {
        jdbc.update("INSERT INTO salle (id, center_id, code, nom, isolement, capacite) VALUES (?, ?, ?, ?, ?, ?)", id, centre, code, nom,
                isolement ? "OUI" : "NON", capacite);
    }

    @Override
    protected void creneau(UUID centre, UUID id, String code, String libelle) {
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, ?, ?)", id, centre, code, libelle);
    }

    @Test
    void definir_est_annule_avec_la_transaction_de_l_appelant_et_conserve_quand_elle_est_validee() {
        salle(CENTRE, S1, "S1", "Salle un", false, 1);
        salle(CENTRE, S2, "S2", "Salle deux", true, 1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(statut -> {
            commande().definir(CENTRE, Set.of(S1));
            statut.setRollbackOnly();
        });
        assertThat(lectures().salleDuCentre(CENTRE, S1)).get().extracting(s -> s.estSalleDIsolement()).isEqualTo(false);
        assertThat(lectures().salleDuCentre(CENTRE, S2)).get().extracting(s -> s.estSalleDIsolement()).isEqualTo(true);

        transaction.executeWithoutResult(statut -> commande().definir(CENTRE, Set.of(S1)));
        assertThat(lectures().salleDuCentre(CENTRE, S1)).get().extracting(s -> s.estSalleDIsolement()).isEqualTo(true);
        assertThat(lectures().salleDuCentre(CENTRE, S2)).get().extracting(s -> s.estSalleDIsolement()).isEqualTo(false);
    }

    @Test
    void un_creneau_desactive_n_est_plus_propose_a_la_planification_ni_compte_dans_la_capacite() {
        UUID propose = UUID.randomUUID();
        UUID desactive = UUID.randomUUID();
        creneau(CENTRE, propose, "C1", "Matin");
        creneau(CENTRE, desactive, "C2", "Midi");
        jdbc.update("UPDATE position_creneau SET actif = FALSE WHERE id = ?", desactive);

        assertThat(lectures().creneauxParCode(CENTRE)).extracting(c -> c.id()).containsExactly(propose);
        assertThat(lectures().contientLeCreneau(CENTRE, desactive)).isFalse();
        assertThat(lectures().contientLeCreneau(CENTRE, propose)).isTrue();
        assertThat(lectures().nombreDeCreneaux(CENTRE)).isEqualTo(1);
    }

    @Test
    void reserver_le_centre_ne_cree_aucune_ligne_et_ignore_un_centre_inconnu() {
        jdbc.update("INSERT INTO centers (id) VALUES (?)", CENTRE);
        VerrouDuCentreJdbc verrou = new VerrouDuCentreJdbc(jdbc);

        new TransactionTemplate(transactionManager).executeWithoutResult(statut -> {
            verrou.reserverLeCentre(CENTRE);
            verrou.reserverLeCentre(INCONNU);
        });

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM centers", Integer.class)).isEqualTo(1);
    }
}
