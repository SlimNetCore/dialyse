package com.hemodialyse.backend.contexts.absencespatients.contrat;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrat des lectures du suivi exécuté contre les adaptateurs JDBC sur H2 (mode PostgreSQL), avec les trois tables lues :
 * absences, patients et séances. Variante PostgreSQL 16 : {@code LecturesDuSuiviPostgresContractIT}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:absences-contrat-suivi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LecturesDuSuiviJdbcContratTest extends LecturesDuSuiviJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void creerLesTablesEtViderLesLignes() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS absence_patient (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "patient_id UUID NOT NULL, date_seance DATE NOT NULL, source VARCHAR(20) NOT NULL, "
                + "statut VARCHAR(20) NOT NULL, motif VARCHAR(30), commentaire VARCHAR(1000), forfait_id UUID, "
                + "forfait_libelle VARCHAR(255), prix_ttc NUMERIC(14,2) NOT NULL DEFAULT 0, "
                + "taux_tva NUMERIC(6,2) NOT NULL DEFAULT 0, montant_ht NUMERIC(14,2) NOT NULL DEFAULT 0, declaree_par UUID, "
                + "declaree_le TIMESTAMP WITH TIME ZONE, qualifiee_par UUID, qualifiee_le TIMESTAMP WITH TIME ZONE, "
                + "date_rattrapage DATE, modifiee_par UUID, modifiee_le TIMESTAMP WITH TIME ZONE, "
                + "UNIQUE (center_id, patient_id, date_seance))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS patients (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "code_patient VARCHAR(255), nom VARCHAR(255) NOT NULL, prenom VARCHAR(255) NOT NULL, "
                + "sexe VARCHAR(255) NOT NULL, date_admission DATE NOT NULL, numero_assurance VARCHAR(255) NOT NULL, "
                + "type_patient VARCHAR(255) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS seances (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "patient_id UUID NOT NULL, date_seance DATE NOT NULL, statut VARCHAR(255) NOT NULL)");
        viderLesLignesDeTest();
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
