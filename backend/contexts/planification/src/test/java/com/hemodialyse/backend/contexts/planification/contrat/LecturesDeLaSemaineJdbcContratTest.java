package com.hemodialyse.backend.contexts.planification.contrat;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Contrat des lectures de la semaine exécuté contre les adapters JDBC sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : {@code LecturesDeLaSemaineJdbcPostgresContractIT}. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:planification-contrat-semaine;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LecturesDeLaSemaineJdbcContratTest extends LecturesDeLaSemaineJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void creerLesTablesEtViderLesLignes() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS patients (id UUID PRIMARY KEY, center_id UUID NOT NULL, nom VARCHAR(255), "
                + "prenom VARCHAR(255), date_admission DATE, numero_assurance VARCHAR(255), sexe VARCHAR(10), type_patient VARCHAR(30), "
                + "etat_patient VARCHAR(30), date_evenement_etat DATE, en_sommeil BOOLEAN, salle_id UUID, position_id UUID, "
                + "generateur_id UUID, jour_dimanche BOOLEAN DEFAULT FALSE, jour_lundi BOOLEAN DEFAULT FALSE, "
                + "jour_mardi BOOLEAN DEFAULT FALSE, jour_mercredi BOOLEAN DEFAULT FALSE, jour_jeudi BOOLEAN DEFAULT FALSE, "
                + "jour_vendredi BOOLEAN DEFAULT FALSE, jour_samedi BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS serologies_patient (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "patient_id UUID NOT NULL, marqueur VARCHAR(15) NOT NULL, resultat VARCHAR(15) NOT NULL, "
                + "date_prelevement DATE NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                + "updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS seances (id UUID PRIMARY KEY, center_id UUID NOT NULL, patient_id UUID NOT NULL, "
                + "date_seance DATE NOT NULL, statut VARCHAR(30) NOT NULL, salle_id UUID, creneau_id UUID, generateur_id UUID)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS deplacement_temporaire (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "patient_id UUID NOT NULL, date_seance DATE NOT NULL, salle_id UUID NOT NULL, creneau_id UUID NOT NULL, "
                + "generateur_id UUID NOT NULL, motif VARCHAR(255), cree_le TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS position_creneau (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "code VARCHAR(50) NOT NULL, libelle VARCHAR(255) NOT NULL)");
        vider();
    }

    private void vider() {
        for (Object centre : new Object[]{CENTRE.valeur(), AUTRE_CENTRE}) {
            for (String table : new String[]{"seances", "deplacement_temporaire", "serologies_patient", "patients", "position_creneau"}) {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", centre);
            }
        }
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
