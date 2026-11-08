package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.ConstantesDesSeancesJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.LecturesDesSeancesRealiseesJdbc;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ConstantesDesSeancesPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.LiensDExclusionPublies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesAvecLeurPlacePubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesPubliees;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Partie JDBC du contrat SE7d (H2 et PostgreSQL 16 : seule la connexion change). La table réelle des séances vient de {@link TableDesSeances} ;
 * {@code patients} et {@code volet_paramedical} sont créées avec les colonnes lues et les colonnes obligatoires du schéma réel (sur
 * PostgreSQL elles existent déjà : {@code CREATE TABLE IF NOT EXISTS} ne fait rien).
 */
abstract class SeancesRealiseesEtConstantesJdbcContrat extends SeancesRealiseesEtConstantesContrat {

    protected abstract JdbcTemplate jdbc();

    protected void preparer() {
        TableDesSeances.creer(jdbc());
        jdbc().execute("CREATE TABLE IF NOT EXISTS patients (id UUID PRIMARY KEY, center_id UUID NOT NULL, code_patient VARCHAR(50), "
                + "nom VARCHAR(255) NOT NULL, prenom VARCHAR(255) NOT NULL, sexe VARCHAR(10), date_admission DATE, "
                + "numero_assurance VARCHAR(100), type_patient VARCHAR(50))");
        jdbc().execute("CREATE TABLE IF NOT EXISTS volet_paramedical (id UUID PRIMARY KEY, anticoagulant VARCHAR(100), "
                + "center_id UUID NOT NULL, created_at TIMESTAMP WITH TIME ZONE, debit_sang_ml_min INTEGER, duree_minutes INTEGER, "
                + "incidents TEXT, poids_apres_kg NUMERIC(38, 2), poids_avant_kg NUMERIC(38, 2), seance_id UUID NOT NULL UNIQUE, "
                + "ta_apres VARCHAR(30), ta_avant VARCHAR(30), type_dialysat VARCHAR(100), ultrafiltration_ml NUMERIC(38, 2), "
                + "updated_at TIMESTAMP WITH TIME ZONE)");
        vider();
    }

    protected void vider() {
        for (String table : new String[]{"volet_paramedical", "seances", "patients"}) {
            jdbc().update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
        }
    }

    @Override
    protected SeancesRealiseesPubliees realisees() {
        return new LecturesDesSeancesRealiseesJdbc(jdbc());
    }

    @Override
    protected LiensDExclusionPublies liens() {
        return new LecturesDesSeancesRealiseesJdbc(jdbc());
    }

    @Override
    protected SeancesRealiseesAvecLeurPlacePubliees avecPlace() {
        return new LecturesDesSeancesRealiseesJdbc(jdbc());
    }

    @Override
    protected ConstantesDesSeancesPubliees constantes() {
        return new ConstantesDesSeancesJdbc(jdbc());
    }

    @Override
    protected void patient(UUID id, UUID centre, String nom, String prenom) {
        String suffixe = id.toString().substring(24);
        jdbc().update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, type_patient) "
                        + "VALUES (?, ?, ?, ?, ?, 'M', ?, ?, 'NON_VACANCIER')",
                id, centre, "SE7D-" + suffixe, nom, prenom, Date.valueOf(LocalDate.of(2024, 1, 1)), "ASS-SE7D-" + suffixe);
    }

    @Override
    protected void ficheDansLeCentre(UUID patient, UUID centre) {
        jdbc().update("UPDATE patients SET center_id = ? WHERE id = ?", centre, patient);
    }

    @Override
    protected void seance(UUID id, UUID centre, UUID patient, LocalDate date, String statut, Instant creeLe) {
        jdbc().update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, centre, patient, Date.valueOf(date), statut, creeLe == null ? null : Timestamp.from(creeLe));
    }

    @Override
    protected void place(UUID seance, UUID salle, UUID creneau, UUID generateur) {
        jdbc().update("UPDATE seances SET salle_id = ?, creneau_id = ?, generateur_id = ? WHERE id = ?", salle, creneau, generateur, seance);
    }

    @Override
    protected void lienDExclusion(UUID seance, UUID absence) {
        jdbc().update("UPDATE seances SET exclusion_absence_id = ? WHERE id = ?", absence, seance);
    }

    @Override
    protected void volet(UUID centre, UUID seance, BigDecimal poidsAvant, BigDecimal poidsApres, String tensionAvant, String tensionApres,
                         Integer debit, BigDecimal ultrafiltration, Integer duree) {
        jdbc().update("INSERT INTO volet_paramedical (id, center_id, seance_id, poids_avant_kg, poids_apres_kg, ta_avant, ta_apres, "
                        + "debit_sang_ml_min, ultrafiltration_ml, duree_minutes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), centre, seance, poidsAvant, poidsApres, tensionAvant, tensionApres, debit, ultrafiltration, duree);
    }
}
