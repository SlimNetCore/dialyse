package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Prescriptions;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.PrescriptionsJdbc;
import org.springframework.jdbc.core.JdbcTemplate;

/** Contrat des prescriptions sur la table réelle (H2 et PostgreSQL 16 : sous-classes). */
abstract class PrescriptionsJdbcContrat extends PrescriptionsContrat {

    protected abstract JdbcTemplate jdbc();

    /** Schéma H2 de la table (colonnes et longueurs du schéma réel V1 et de la colonne du poids sec). */
    static void creerLaTable(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS prescriptions_medicales (id UUID PRIMARY KEY, anticoag_type_prescrit VARCHAR(20), "
                + "center_id UUID NOT NULL, created_at TIMESTAMP WITH TIME ZONE, date_prescription DATE NOT NULL, duree_cible_min INTEGER, "
                + "epo_article_id UUID, epo_dose_ui INTEGER, epo_frequence_unite VARCHAR(10), epo_frequence_valeur INTEGER, "
                + "epo_voie VARCHAR(10), fer_article_id UUID, fer_dose_mg INTEGER, fer_frequence_unite VARCHAR(10), "
                + "fer_frequence_valeur INTEGER, fer_voie VARCHAR(10), medecin_id UUID, patient_id UUID NOT NULL, "
                + "poids_sec_cible_kg NUMERIC(5, 2), qb_cible INTEGER, qd_cible INTEGER, type_dialyseur_prescrit VARCHAR(100), "
                + "uf_max_ml INTEGER, updated_at TIMESTAMP WITH TIME ZONE)");
    }

    protected void vider() {
        for (Object centre : new Object[]{CENTRE_A.valeur(), CENTRE_B.valeur()}) {
            jdbc().update("DELETE FROM prescriptions_medicales WHERE center_id = ?", centre);
        }
    }

    @Override
    protected Prescriptions prescriptions() {
        return new PrescriptionsJdbc(jdbc());
    }
}
