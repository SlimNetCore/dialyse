package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.SeancesDeLaRepriseJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.SeancesDuCahierEtForfaitsImposesJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.SeancesRealiseesDesCentresJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.StatistiquesDesSeancesJdbc;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ForfaitsImposesAuxSeancesPublies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDeLaReprisePubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDuCahierPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesDesCentresPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.StatistiquesDesSeancesPubliees;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Partie JDBC du contrat SE7e (H2 et PostgreSQL 16 : seule la connexion change). La table réelle des séances vient de {@link TableDesSeances} ;
 * les autres tables lues (fiche, payeur, agence, caisse, volet paramédical, prescription) sont créées avec les colonnes lues et les colonnes
 * obligatoires du schéma réel (sur PostgreSQL elles existent déjà : {@code CREATE TABLE IF NOT EXISTS} ne fait rien). Toutes les lignes
 * posées portent un centre A, B ou C du contrat et sont effacées avant et après chaque cas : aucune autre donnée n'est touchée.
 */
abstract class PilotageEtRapportsDesSeancesJdbcContrat extends PilotageEtRapportsDesSeancesContrat {

    private static final List<String> TABLES = List.of("volet_paramedical", "volet_medical", "prescriptions_medicales", "seances", "patients", "centre_payeur",
            "agence", "caisse_assurance");

    protected abstract JdbcTemplate jdbc();

    protected void preparer() {
        TableDesSeances.creer(jdbc());
        jdbc().execute("CREATE TABLE IF NOT EXISTS patients (id UUID PRIMARY KEY, center_id UUID NOT NULL, code_patient VARCHAR(50), "
                + "nom VARCHAR(255) NOT NULL, prenom VARCHAR(255) NOT NULL, sexe VARCHAR(255) NOT NULL, date_admission DATE NOT NULL, "
                + "numero_assurance VARCHAR(255) NOT NULL, type_patient VARCHAR(255) NOT NULL, centre_payeur_id UUID)");
        jdbc().execute("CREATE TABLE IF NOT EXISTS caisse_assurance (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(50), nom VARCHAR(255), "
                + "type_caisse VARCHAR(50) DEFAULT 'STANDARD')");
        jdbc().execute("CREATE TABLE IF NOT EXISTS agence (id UUID PRIMARY KEY, center_id UUID NOT NULL, caisse_id UUID, code VARCHAR(50), "
                + "nom VARCHAR(255))");
        jdbc().execute("CREATE TABLE IF NOT EXISTS centre_payeur (id UUID PRIMARY KEY, center_id UUID NOT NULL, agence_id UUID, code VARCHAR(50), "
                + "nom VARCHAR(255), adresse VARCHAR(500))");
        jdbc().execute("CREATE TABLE IF NOT EXISTS volet_paramedical (id UUID PRIMARY KEY, anticoagulant VARCHAR(100), "
                + "center_id UUID NOT NULL, created_at TIMESTAMP WITH TIME ZONE, debit_sang_ml_min INTEGER, duree_minutes INTEGER, "
                + "incidents TEXT, poids_apres_kg NUMERIC(38, 2), poids_avant_kg NUMERIC(38, 2), seance_id UUID NOT NULL UNIQUE, "
                + "ta_apres VARCHAR(30), ta_avant VARCHAR(30), type_dialysat VARCHAR(100), ultrafiltration_ml NUMERIC(38, 2), "
                + "updated_at TIMESTAMP WITH TIME ZONE)");
        jdbc().execute("CREATE TABLE IF NOT EXISTS volet_medical (id UUID PRIMARY KEY, center_id UUID NOT NULL, seance_id UUID NOT NULL UNIQUE, "
                + "created_at TIMESTAMP WITH TIME ZONE, updated_at TIMESTAMP WITH TIME ZONE)");
        jdbc().execute("CREATE TABLE IF NOT EXISTS prescriptions_medicales (id UUID PRIMARY KEY, center_id UUID NOT NULL, patient_id UUID NOT NULL, "
                + "date_prescription DATE NOT NULL, poids_sec_cible_kg NUMERIC(5, 2), created_at TIMESTAMP WITH TIME ZONE, "
                + "updated_at TIMESTAMP WITH TIME ZONE)");
        vider();
    }

    protected void vider() {
        for (String table : TABLES) {
            jdbc().update("DELETE FROM " + table + " WHERE center_id IN (?, ?, ?)", CENTRE_A, CENTRE_B, CENTRE_C);
        }
    }

    @Override
    protected SeancesRealiseesDesCentresPubliees pilotage() {
        return new SeancesRealiseesDesCentresJdbc(jdbc(), new com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture
                .ChaineDesPayeursParLAnnuaire(new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence
                .PayeursDuCentreJdbc(jdbc())));
    }

    @Override
    protected StatistiquesDesSeancesPubliees statistiques() {
        return new StatistiquesDesSeancesJdbc(jdbc());
    }

    @Override
    protected SeancesDuCahierPubliees cahier() {
        return new SeancesDuCahierEtForfaitsImposesJdbc(jdbc());
    }

    @Override
    protected ForfaitsImposesAuxSeancesPublies forfaits() {
        return new SeancesDuCahierEtForfaitsImposesJdbc(jdbc());
    }

    @Override
    protected SeancesDeLaReprisePubliees reprise() {
        return new SeancesDeLaRepriseJdbc(jdbc());
    }

    @Override
    protected void payeur(UUID payeur, UUID caisse, UUID centre, String codeCaisse, String nomCaisse) {
        UUID agence = UUID.randomUUID();
        jdbc().update("INSERT INTO caisse_assurance (id, center_id, code, nom) VALUES (?, ?, ?, ?)", caisse, centre, codeCaisse, nomCaisse);
        jdbc().update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?, ?, ?, ?, ?)", agence, centre, caisse, "AG-" + codeCaisse,
                "Agence");
        jdbc().update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?, ?, ?, ?, ?)", payeur, centre, agence,
                "CP-" + codeCaisse, "Payeur");
    }

    @Override
    protected void patient(UUID id, UUID centre, UUID payeur) {
        String suffixe = id.toString().substring(24);
        jdbc().update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, type_patient, "
                        + "centre_payeur_id) VALUES (?, ?, ?, 'Nom', 'Pre', 'M', ?, ?, 'NON_VACANCIER', ?)",
                id, centre, "SE7E-" + suffixe, Date.valueOf(LocalDate.of(2024, 1, 1)), "ASS-SE7E-" + suffixe, payeur);
    }

    @Override
    protected void seance(UUID id, UUID centre, UUID patient, LocalDate date, String statut, Instant creeLe) {
        jdbc().update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, centre, patient, Date.valueOf(date), statut, creeLe == null ? null : Timestamp.from(creeLe));
    }

    @Override
    protected void volet(UUID centre, UUID seance, BigDecimal poidsAvant, BigDecimal poidsApres, String tensionAvant,
                         BigDecimal ultrafiltration) {
        jdbc().update("INSERT INTO volet_paramedical (id, center_id, seance_id, poids_avant_kg, poids_apres_kg, ta_avant, ultrafiltration_ml) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), centre, seance, poidsAvant, poidsApres, tensionAvant, ultrafiltration);
    }

    @Override
    protected void voletMedical(UUID centre, UUID seance) {
        jdbc().update("INSERT INTO volet_medical (id, center_id, seance_id) VALUES (?, ?, ?)", UUID.randomUUID(), centre, seance);
    }

    @Override
    protected void payeurDontLAgenceEstDUnAutreCentre(UUID payeur, UUID caisse, UUID centreDuPayeur, UUID centreDeLAgence, String codeCaisse,
                                                      String nomCaisse) {
        UUID agence = UUID.randomUUID();
        jdbc().update("INSERT INTO caisse_assurance (id, center_id, code, nom) VALUES (?, ?, ?, ?)", caisse, centreDeLAgence, codeCaisse, nomCaisse);
        jdbc().update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?, ?, ?, ?, ?)", agence, centreDeLAgence, caisse,
                "AG-" + codeCaisse, "Agence");
        jdbc().update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?, ?, ?, ?, ?)", payeur, centreDuPayeur, agence,
                "CP-" + codeCaisse, "Payeur");
    }

    @Override
    protected void prescription(UUID id, UUID centre, UUID patient, LocalDate date, BigDecimal poidsSec, Instant majLe) {
        jdbc().update("INSERT INTO prescriptions_medicales (id, center_id, patient_id, date_prescription, poids_sec_cible_kg, created_at, "
                        + "updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)", id, centre, patient, Date.valueOf(date), poidsSec,
                Timestamp.from(Instant.parse("2026-01-01T10:00:00Z")), majLe == null ? null : Timestamp.from(majLe));
    }

    @Override
    protected void forfaitImpose(UUID seance, UUID forfait) {
        jdbc().update("UPDATE seances SET forfait_override_id = ? WHERE id = ?", forfait, seance);
    }

    @Override
    protected void facture(UUID seance, UUID facture) {
        jdbc().update("UPDATE seances SET facture_id = ? WHERE id = ?", facture, seance);
    }

    @Override
    protected String statutDe(UUID seance) {
        List<String> statuts = jdbc().queryForList("SELECT statut FROM seances WHERE id = ?", String.class, seance);
        return statuts.isEmpty() ? null : statuts.get(0);
    }

    @Override
    protected UUID patientDe(UUID seance) {
        return jdbc().queryForObject("SELECT patient_id FROM seances WHERE id = ?", UUID.class, seance);
    }
}
