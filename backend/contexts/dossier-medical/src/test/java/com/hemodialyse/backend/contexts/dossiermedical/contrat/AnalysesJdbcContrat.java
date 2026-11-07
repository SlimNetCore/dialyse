package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyses;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Periode;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.AnalysesJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat des analyses sur la table réelle (H2 et PostgreSQL 16 : sous-classes). */
abstract class AnalysesJdbcContrat extends AnalysesContrat {
    protected abstract JdbcTemplate jdbc();

    protected void vider() {
        for (Object centre : new Object[]{CENTRE_A.valeur(), CENTRE_B.valeur()}) {
            jdbc().update("DELETE FROM resultats_analyses WHERE center_id = ?", centre);
        }
    }

    @Override
    protected Analyses analyses() {
        return new AnalysesJdbc(jdbc());
    }

    @Test
    void un_bilan_importe_sans_dates_de_creation_est_relu_sans_erreur() {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO resultats_analyses (id, center_id, patient_id, date_prelevement, hb_g_dl) VALUES (?,?,?,?,?)", id,
                CENTRE_A.valeur(), PATIENT.valeur(), java.sql.Date.valueOf("2026-01-05"), new java.math.BigDecimal("10.5"));
        var relue = analyses().trouverEnVerrouillant(CENTRE_A, PATIENT, id).orElseThrow();
        assertThat(relue.creeLe()).isNull();
        assertThat(relue.modifieLe()).isNull();
        assertThat(relue.mesures().hemoglobine()).isEqualByComparingTo("10.5");
        assertThat(analyses().duPatient(CENTRE_A, PATIENT, Periode.ILLIMITEE, 0, 10).items()).hasSize(1);
    }

    @Test
    void les_decimales_au_dela_de_deux_sont_arrondies_par_la_base() {
        var a = analyse(CENTRE_A, PATIENT, java.time.LocalDate.of(2026, 1, 5), "11.555", T0);
        analyses().ajouter(a);
        assertThat(analyses().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow().mesures().hemoglobine()).isEqualByComparingTo("11.56");
    }

    @Test
    void la_table_garde_ses_colonnes_et_aucune_colonne_n_est_ajoutee() {
        List<String> colonnes = jdbc().queryForList("SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = 'resultats_analyses'", String.class);
        assertThat(colonnes).containsExactlyInAnyOrder("id", "albumine_g_dl", "calcium_mg_dl", "center_id", "created_at", "creatinine_mg_dl",
                "crp_mg_l", "cstf_pct", "date_prelevement", "epo_endogene_mui_ml", "ferritine_ng_ml", "hb_g_dl", "ht_pct", "kt_v_mensuel",
                "patient_id", "phosphore_mg_dl", "plaquettes", "proteines_g_dl", "pth_pg_ml", "updated_at", "uree_post_mg_dl", "uree_pre_mg_dl");
    }
}
