package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.FacturationDesSeancesJdbc;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ExclusionsEtForfaitsDesSeancesPublies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesAFacturerPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDesFacturesPubliees;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Partie JDBC du contrat (H2 et PostgreSQL 16 : seule la connexion change). La table réelle des séances vient de {@link TableDesSeances}. */
abstract class FacturationDesSeancesJdbcContrat extends FacturationDesSeancesContrat {

    protected abstract JdbcTemplate jdbc();

    /** Tables du Patient lues par la jointure de l'adapter (colonnes lues seulement) ; sur PostgreSQL elles existent déjà (Flyway). */
    protected void preparer() {
        TableDesSeances.creer(jdbc());
        jdbc().execute("CREATE TABLE IF NOT EXISTS patients (id UUID PRIMARY KEY, center_id UUID NOT NULL, code_patient VARCHAR(50), "
                + "nom VARCHAR(255), prenom VARCHAR(255), sexe VARCHAR(10), date_admission DATE, numero_assurance VARCHAR(100), "
                + "type_patient VARCHAR(50), etat_patient VARCHAR(50), centre_payeur_id UUID)");
        jdbc().execute("CREATE TABLE IF NOT EXISTS centre_payeur (id UUID PRIMARY KEY, center_id UUID NOT NULL, agence_id UUID, "
                + "code VARCHAR(50), nom VARCHAR(255))");
        jdbc().execute("CREATE TABLE IF NOT EXISTS factures (id UUID PRIMARY KEY, center_id UUID NOT NULL, patient_id UUID NOT NULL, "
                + "numero_facture VARCHAR(120) NOT NULL, period_start DATE NOT NULL, period_end DATE NOT NULL, date_facturation DATE NOT NULL, "
                + "tva_rate DECIMAL(5,2) NOT NULL, total_ht DECIMAL(14,2) NOT NULL, total_tva DECIMAL(14,2) NOT NULL, "
                + "total_ttc DECIMAL(14,2) NOT NULL)");
        vider();
    }

    protected void vider() {
        for (String table : new String[]{"seances", "factures", "patients", "centre_payeur"}) {
            jdbc().update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
        }
    }

    @org.junit.jupiter.api.Test
    void should_write_the_author_and_the_instant_of_the_forfait_imposed() {
        decor();
        UUID id = UUID.randomUUID();
        seance(id, CENTRE_A, AMRANI, LocalDate.of(2026, 8, 2), "VALIDEE", null);

        exclusions().poserLeForfait(CENTRE_A, id, PREMIUM, "Forfait HD Premium", new BigDecimal("5200.00"), "medecin",
                java.time.Instant.parse("2026-10-20T08:00:00Z"));

        assertThat(jdbc().queryForObject("SELECT forfait_override_updated_by FROM seances WHERE id = ?", String.class, id))
                .isEqualTo("medecin");
        assertThat(jdbc().queryForObject("SELECT forfait_override_updated_at FROM seances WHERE id = ?", java.sql.Timestamp.class, id)
                .toInstant()).isEqualTo(java.time.Instant.parse("2026-10-20T08:00:00Z"));
    }

    @org.junit.jupiter.api.Test
    void should_keep_the_memory_of_the_exclusion_in_the_two_columns_added_by_v39() {
        decor();
        UUID id = UUID.randomUUID();
        UUID absence = UUID.randomUUID();
        seance(id, CENTRE_A, AMRANI, LocalDate.of(2026, 8, 2), "SIGNEE", null);

        exclusions().exclure(CENTRE_A, id, "SIGNEE");
        exclusions().lierALAbsence(CENTRE_A, id, absence);

        assertThat(jdbc().queryForObject("SELECT statut || '/' || statut_avant_exclusion FROM seances WHERE id = ?", String.class, id))
                .isEqualTo("ABSENT/SIGNEE");
        assertThat(jdbc().queryForObject("SELECT exclusion_absence_id FROM seances WHERE id = ?", UUID.class, id)).isEqualTo(absence);
    }

    @Override
    protected SeancesAFacturerPubliees aFacturer() {
        return new FacturationDesSeancesJdbc(jdbc());
    }

    @Override
    protected ExclusionsEtForfaitsDesSeancesPublies exclusions() {
        return new FacturationDesSeancesJdbc(jdbc());
    }

    @Override
    protected SeancesDesFacturesPubliees facturees() {
        return new FacturationDesSeancesJdbc(jdbc());
    }

    @Override
    protected com.hemodialyse.backend.contexts.seance.publishedlanguage.LiaisonDesSeancesAUneFacturePubliee liaison() {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.persistence.LiaisonDesSeancesJdbc(jdbc());
    }

    @Override
    protected UUID factureDe(UUID seance) {
        return jdbc().queryForObject("SELECT facture_id FROM seances WHERE id = ?", UUID.class, seance);
    }

    @Override
    protected void facture(UUID id, UUID centre) {
        jdbc().update("INSERT INTO factures (id, center_id, patient_id, numero_facture, period_start, period_end, date_facturation, "
                        + "tva_rate, total_ht, total_tva, total_ttc) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0)",
                id, centre, UUID.randomUUID(), "F-" + id, Date.valueOf(LocalDate.of(2026, 8, 1)), Date.valueOf(LocalDate.of(2026, 8, 31)),
                Date.valueOf(LocalDate.of(2026, 9, 1)));
    }

    @Override
    protected void patient(UUID id, UUID centre, String code, String nom, String prenom, String etat) {
        jdbc().update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient) VALUES (?, ?, ?, ?, ?, 'F', ?, ?, 'NON_VACANCIER', ?)",
                id, centre, code, nom, prenom, Date.valueOf(LocalDate.of(2026, 1, 10)), "ASS-" + code, etat);
    }

    @Override
    protected void payeur(UUID patient, UUID centre, UUID centrePayeur, UUID agence) {
        jdbc().update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?, ?, ?, ?, 'CNAS Alger')", centrePayeur,
                centre, agence, "CP-" + centrePayeur.toString().substring(30));
        jdbc().update("UPDATE patients SET centre_payeur_id = ? WHERE id = ?", centrePayeur, patient);
    }

    @Override
    protected void seance(UUID id, UUID centre, UUID patient, LocalDate date, String statut, UUID facture) {
        jdbc().update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut, facture_id) VALUES (?, ?, ?, ?, ?, ?)",
                id, centre, patient, Date.valueOf(date), statut, facture);
    }

    @Override
    protected void surcharge(UUID seance, UUID forfait, String nom, BigDecimal prix, String code) {
        jdbc().update("UPDATE seances SET forfait_override_id = ?, forfait_override_nom = ?, forfait_override_prix = ?, "
                + "forfait_override_code = ? WHERE id = ?", forfait, nom, prix, code, seance);
    }

    @Override
    protected void memoire(UUID seance, String statutAvant) {
        jdbc().update("UPDATE seances SET statut_avant_exclusion = ? WHERE id = ?", statutAvant, seance);
    }

    @Override
    protected String statutDe(UUID seance) {
        return jdbc().queryForObject("SELECT statut FROM seances WHERE id = ?", String.class, seance);
    }

    @Override
    protected String codeDuForfaitDe(UUID seance) {
        return jdbc().queryForObject("SELECT forfait_override_code FROM seances WHERE id = ?", String.class, seance);
    }

    @Override
    protected String memoireDe(UUID seance) {
        return jdbc().queryForObject("SELECT statut_avant_exclusion FROM seances WHERE id = ?", String.class, seance);
    }

    @Override
    protected UUID absenceDe(UUID seance) {
        return jdbc().queryForObject("SELECT exclusion_absence_id FROM seances WHERE id = ?", UUID.class, seance);
    }
}
