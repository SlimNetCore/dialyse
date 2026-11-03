package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Contrat des patients sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : {@code PatientsJdbcPostgresContractIT}. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:patient-couverture-contrat;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PatientsJdbcContratTest extends PatientsJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void creerLesTablesEtViderLesLignes() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS patients (id UUID PRIMARY KEY, center_id UUID NOT NULL, code_patient VARCHAR(255), "
                + "civilite VARCHAR(50), nom VARCHAR(255) NOT NULL, prenom VARCHAR(255) NOT NULL, sexe VARCHAR(10) NOT NULL, "
                + "groupe_sanguin VARCHAR(10), nombre_enfants INTEGER, date_admission DATE NOT NULL, en_sommeil BOOLEAN, "
                + "date_naissance DATE, lieu_naissance VARCHAR(255), situation_familiale VARCHAR(50), profession1 VARCHAR(255), "
                + "adresse VARCHAR(500), tel_personnel VARCHAR(50), tel_mobile VARCHAR(50), tel_bureau VARCHAR(50), "
                + "email VARCHAR(255), numero_assurance VARCHAR(255) NOT NULL, type_patient VARCHAR(30) NOT NULL, "
                + "etat_patient VARCHAR(30), date_evenement_etat DATE, qualite_assure VARCHAR(30), observation TEXT, "
                + "sous_kt BOOLEAN, epo_enabled BOOLEAN, epo_date DATE, fer_enabled BOOLEAN, fer_date DATE, photo_base64 TEXT, "
                + "centre_payeur_id UUID, medecin_traitant_id UUID, salle_id UUID, position_id UUID, transporteur_aller_id UUID, "
                + "transporteur_retour_id UUID, categorie_transport_id UUID, generateur_id UUID, jour_dimanche BOOLEAN, "
                + "jour_lundi BOOLEAN, jour_mardi BOOLEAN, jour_mercredi BOOLEAN, jour_jeudi BOOLEAN, jour_vendredi BOOLEAN, "
                + "jour_samedi BOOLEAN, assure_nom VARCHAR(255), assure_prenom VARCHAR(255), assure_numero_assurance VARCHAR(255), "
                + "assure_sexe VARCHAR(10), assure_date_naissance DATE, assure_tel_personnel VARCHAR(50), "
                + "assure_tel_mobile VARCHAR(50), assure_tel_bureau VARCHAR(50), assure_adresse VARCHAR(500), "
                + "assure_groupe_sanguin VARCHAR(10), assure_history_json TEXT, pieces_jointes_json TEXT, "
                + "created_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mouvement_patient (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "patient_id UUID NOT NULL, type VARCHAR(30) NOT NULL, date_effet DATE NOT NULL, etat_precedent VARCHAR(30), "
                + "etat_nouveau VARCHAR(30), salle_id UUID, position_id UUID, generateur_id UUID, jours_dialyse VARCHAR(100), "
                + "automatique BOOLEAN NOT NULL, cree_le TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS planning_parametres (center_id UUID NOT NULL PRIMARY KEY, "
                + "jours_ouverts VARCHAR(100) NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS assure (numero_assurance VARCHAR(255) PRIMARY KEY, center_id UUID NOT NULL, "
                + "nom VARCHAR(255), prenom VARCHAR(255), sexe VARCHAR(10), date_naissance DATE, tel_personnel VARCHAR(50), "
                + "tel_mobile VARCHAR(50), tel_bureau VARCHAR(50), adresse VARCHAR(500), groupe_sanguin VARCHAR(10), "
                + "created_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS assure_patient (id UUID PRIMARY KEY, patient_id UUID NOT NULL, "
                + "numero_assurance VARCHAR(255) NOT NULL, center_id UUID NOT NULL, is_primary BOOLEAN NOT NULL, "
                + "date_affectation TIMESTAMP WITH TIME ZONE NOT NULL, date_debut_affectation DATE, date_fin_affectation DATE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS prise_en_charge (id UUID PRIMARY KEY, patient_id UUID NOT NULL, "
                + "center_id UUID NOT NULL, date_debut_demande DATE, date_fin_demande DATE, forfait_demande_id UUID, "
                + "date_debut_effectif DATE, date_fin_effectif DATE, forfait_effectif_id UUID, statut VARCHAR(20) NOT NULL, "
                + "created_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS attestation_droit (id UUID PRIMARY KEY, patient_id UUID NOT NULL, "
                + "center_id UUID NOT NULL, date_debut DATE, date_fin DATE, created_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS salle (id UUID PRIMARY KEY, nom VARCHAR(255))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS position_creneau (id UUID PRIMARY KEY, libelle VARCHAR(255))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS gmao_equipements (id UUID PRIMARY KEY, code VARCHAR(255))");
        for (Object centre : new Object[]{CENTRE.valeur(), AUTRE.valeur()}) {
            jdbc.update("DELETE FROM prise_en_charge WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM attestation_droit WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM assure_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM assure WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM mouvement_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", centre);
        }
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
