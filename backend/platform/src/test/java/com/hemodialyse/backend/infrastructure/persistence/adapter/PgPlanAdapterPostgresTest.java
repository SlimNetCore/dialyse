package com.hemodialyse.backend.infrastructure.persistence.adapter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L'établissement du plan ({@code PREPARE} + {@code EXPLAIN EXECUTE}, transaction en lecture seule) ne se vérifie que sur
 * un vrai PostgreSQL : H2 ne le connaît pas. Ce test n'est exécuté que si une base de test jetable est fournie
 * ({@code -Dpg.test.url=jdbc:postgresql://…}, éventuellement {@code -Dpg.test.user} et {@code -Dpg.test.password}) ;
 * le workflow {@code flyway.yml} le joue sur son PostgreSQL 16. Il a révélé un défaut que seule la production aurait
 * montré : le nettoyage de la connexion échouait après l'établissement du plan.
 */
@EnabledIfSystemProperty(named = "pg.test.url", matches = ".+")
class PgPlanAdapterPostgresTest {

    private static final String TABLE = "zt_plan_seances";
    private static final String REQUETE = "SELECT COUNT(*) FROM " + TABLE
            + " s WHERE s.center_id = $1 AND s.patient_id = $2 AND s.date_seance = $3 AND s.statut IN ($4, $5, $6)";

    private final PgPlanAdapter adapter = new PgPlanAdapter(null);
    private Connection connexion;

    @BeforeEach
    void ouvrir() throws SQLException {
        connexion = DriverManager.getConnection(System.getProperty("pg.test.url"),
                System.getProperty("pg.test.user", "hemo_user"), System.getProperty("pg.test.password", "hemo_pass"));
        try (Statement st = connexion.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + TABLE);
            st.execute("CREATE TABLE " + TABLE
                    + " (id uuid PRIMARY KEY, center_id uuid, patient_id uuid, date_seance date, statut text)");
        }
    }

    @AfterEach
    void fermer() throws SQLException {
        try (Statement st = connexion.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + TABLE);
        }
        connexion.close();
    }

    @Test
    void the_generic_plan_of_a_parameterised_query_is_built_without_running_it() throws SQLException {
        var plan = adapter.planDansTransactionLectureSeule(connexion, REQUETE);

        assertNull(plan.raisonIndisponible());
        assertNotNull(plan.json());
        assertTrue(plan.json().contains("\"Node Type\""));
        assertTrue(plan.json().contains(TABLE));
        assertTrue(plan.texte().contains(TABLE));
    }

    @Test
    void the_connection_is_given_back_in_its_original_state_and_no_prepared_statement_is_left() throws SQLException {
        adapter.planDansTransactionLectureSeule(connexion, REQUETE);

        assertTrue(connexion.getAutoCommit());
        assertFalse(connexion.isReadOnly());
        try (Statement st = connexion.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM pg_prepared_statements WHERE name LIKE 'sup\\_%'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
        // la connexion reste utilisable en écriture et pour une seconde analyse
        try (Statement st = connexion.createStatement()) {
            st.execute("INSERT INTO " + TABLE + " (id) VALUES (gen_random_uuid())");
        }
        assertNull(adapter.planDansTransactionLectureSeule(connexion, REQUETE).raisonIndisponible());
    }

    @Test
    void a_query_postgresql_cannot_plan_is_reported_and_the_connection_still_restored() throws SQLException {
        var plan = adapter.planDansTransactionLectureSeule(connexion, "SELECT * FROM table_qui_n_existe_pas WHERE a = $1");

        assertEquals("PLAN_IMPOSSIBLE", plan.raisonIndisponible());
        assertNull(plan.json());
        assertTrue(connexion.getAutoCommit());
        assertFalse(connexion.isReadOnly());
    }

    @Test
    void the_analysis_never_executes_the_query() throws SQLException {
        try (Statement st = connexion.createStatement()) {
            st.execute("INSERT INTO " + TABLE + " (id) VALUES (gen_random_uuid())");
        }
        // une fonction à effet de bord s'exécuterait si la requête l'était : ici elle ne doit jamais l'être
        try (Statement st = connexion.createStatement()) {
            st.execute("CREATE TEMP TABLE zt_effets (n int)");
            st.execute("CREATE OR REPLACE FUNCTION pg_temp.zt_marque(x uuid) RETURNS boolean LANGUAGE plpgsql VOLATILE AS "
                    + "$f$ BEGIN INSERT INTO zt_effets VALUES (1); RETURN true; END $f$");
        }

        var plan = adapter.planDansTransactionLectureSeule(connexion,
                "SELECT id FROM " + TABLE + " WHERE pg_temp.zt_marque(id)");

        assertNull(plan.raisonIndisponible());
        try (Statement st = connexion.createStatement(); ResultSet rs = st.executeQuery("SELECT count(*) FROM zt_effets")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }
}
