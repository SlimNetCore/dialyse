package com.hemodialyse.backend.contexts.pilotagedirection.contrat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Lectures du Pilotage direction sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : {@code LecturesDuPilotagePostgresContractIT}. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:pilotage-direction-lectures;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LecturesDuPilotageJdbcContratTest extends LecturesDuPilotageContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS societes (id UUID PRIMARY KEY, code VARCHAR(50), raison_sociale VARCHAR(200), "
                + "actif BOOLEAN, created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS centers (id UUID PRIMARY KEY, code VARCHAR(50), name VARCHAR(200), societe_id UUID, "
                + "actif BOOLEAN)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS caisse_assurance (id UUID PRIMARY KEY, code VARCHAR(50), nom VARCHAR(200))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS agence (id UUID PRIMARY KEY, caisse_id UUID)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS centre_payeur (id UUID PRIMARY KEY, agence_id UUID)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS patients (id UUID PRIMARY KEY, center_id UUID, code_patient VARCHAR(50), "
                + "nom VARCHAR(100), prenom VARCHAR(100), sexe VARCHAR(10), date_naissance DATE, numero_assurance VARCHAR(50), "
                + "date_admission DATE, type_patient VARCHAR(30), etat_patient VARCHAR(30), date_evenement_etat DATE, "
                + "qualite_assure VARCHAR(30), sous_kt BOOLEAN, epo_enabled BOOLEAN, fer_enabled BOOLEAN, centre_payeur_id UUID, "
                + "created_at TIMESTAMP)");
        preparer();
    }

    @AfterEach
    void apres() {
        nettoyer();
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
