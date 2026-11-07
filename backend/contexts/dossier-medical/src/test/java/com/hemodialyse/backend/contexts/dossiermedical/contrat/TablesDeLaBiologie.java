package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import org.springframework.jdbc.core.JdbcTemplate;

/** Schéma H2 des deux tables réelles de DM6 (colonnes et types du schéma V1 ; aucune contrainte d'unicité ni clé étrangère). */
final class TablesDeLaBiologie {
    private TablesDeLaBiologie() {
    }

    static void creer(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS observations_biologiques (id UUID PRIMARY KEY, analyte_code VARCHAR(20) NOT NULL, "
                + "analyte_code_display VARCHAR(255), analyte_code_system VARCHAR(10) NOT NULL, center_id UUID NOT NULL, "
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL, date_prelevement DATE NOT NULL, demande_examen_id UUID, patient_id UUID NOT NULL, "
                + "source VARCHAR(20) NOT NULL, statut VARCHAR(15) NOT NULL, unite VARCHAR(20), updated_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                + "valeur_num NUMERIC(14, 4), valeur_texte VARCHAR(500))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS resultats_analyses (id UUID PRIMARY KEY, albumine_g_dl NUMERIC(38, 2), calcium_mg_dl NUMERIC(38, 2), "
                + "center_id UUID NOT NULL, created_at TIMESTAMP WITH TIME ZONE, creatinine_mg_dl NUMERIC(38, 2), crp_mg_l NUMERIC(38, 2), "
                + "cstf_pct NUMERIC(38, 2), date_prelevement DATE NOT NULL, epo_endogene_mui_ml NUMERIC(38, 2), ferritine_ng_ml NUMERIC(38, 2), "
                + "hb_g_dl NUMERIC(38, 2), ht_pct NUMERIC(38, 2), kt_v_mensuel NUMERIC(38, 2), patient_id UUID NOT NULL, "
                + "phosphore_mg_dl NUMERIC(38, 2), plaquettes INTEGER, proteines_g_dl NUMERIC(38, 2), pth_pg_ml NUMERIC(38, 2), "
                + "updated_at TIMESTAMP WITH TIME ZONE, uree_post_mg_dl NUMERIC(38, 2), uree_pre_mg_dl NUMERIC(38, 2))");
    }
}
