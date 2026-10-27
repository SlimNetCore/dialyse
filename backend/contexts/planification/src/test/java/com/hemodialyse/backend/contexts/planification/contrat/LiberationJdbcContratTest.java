package com.hemodialyse.backend.contexts.planification.contrat;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrat de la libération des places sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : {@code LiberationJdbcPostgresContractIT}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:planification-contrat-liberation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LiberationJdbcContratTest extends LiberationJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void creerLesTablesEtViderLesLignes() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS planning_parametres (center_id UUID NOT NULL PRIMARY KEY, "
                + "jours_ouverts VARCHAR(100) NOT NULL, salles_isolement TEXT, patients_par_infirmier INTEGER, "
                + "patients_par_poste_serie INTEGER, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS patients (id UUID PRIMARY KEY, center_id UUID NOT NULL, nom VARCHAR(255), "
                + "prenom VARCHAR(255), sexe VARCHAR(10), type_patient VARCHAR(30), numero_assurance VARCHAR(255), date_admission DATE, etat_patient VARCHAR(30), date_evenement_etat DATE, en_sommeil BOOLEAN, salle_id UUID, position_id UUID, "
                + "generateur_id UUID, jour_dimanche BOOLEAN DEFAULT FALSE, jour_lundi BOOLEAN DEFAULT FALSE, "
                + "jour_mardi BOOLEAN DEFAULT FALSE, jour_mercredi BOOLEAN DEFAULT FALSE, jour_jeudi BOOLEAN DEFAULT FALSE, "
                + "jour_vendredi BOOLEAN DEFAULT FALSE, jour_samedi BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mouvement_patient (id UUID PRIMARY KEY, center_id UUID NOT NULL, patient_id UUID NOT NULL, "
                + "type VARCHAR(30) NOT NULL, date_effet DATE NOT NULL, etat_precedent VARCHAR(30), etat_nouveau VARCHAR(30), "
                + "salle_id UUID, position_id UUID, generateur_id UUID, jours_dialyse VARCHAR(100), automatique BOOLEAN NOT NULL, "
                + "cree_le TIMESTAMP NOT NULL)");
        nettoyer();
    }

    private void nettoyer() {
        jdbc.execute("ALTER TABLE mouvement_patient DROP CONSTRAINT IF EXISTS zz_p7_echec");
        for (Object centre : new Object[]{CENTRE.valeur(), AUTRE.valeur()}) {
            for (String table : new String[]{"mouvement_patient", "patients", "planning_parametres"}) {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", centre);
            }
        }
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
