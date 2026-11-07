package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DossiersMedicauxDeBase;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.DossiersMedicauxDeBaseJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat des dossiers médicaux de base sur la table réelle (H2 et PostgreSQL 16 : sous-classes). Le doublon pour un même patient est
 * impossible sur la table réelle (unicité de {@code patient_id}) : la lecture robuste d'un doublon (le plus ancien, WARN avec
 * l'identifiant seul) ne concerne qu'une base sans cette contrainte et n'est pas rejouée ici.
 */
abstract class DossiersMedicauxDeBaseJdbcContrat extends DossiersMedicauxDeBaseContrat {
    protected abstract JdbcTemplate jdbc();

    static void creer(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS dossier_medical_patient (id UUID PRIMARY KEY, patient_id UUID NOT NULL UNIQUE, center_id UUID NOT NULL, "
                + "nephropathie_initiale VARCHAR(255), date_mise_en_dialyse DATE, hepatite_b_statut VARCHAR(20), hepatite_c_statut VARCHAR(20), "
                + "observation_globale TEXT, conclusion_medicale TEXT, created_at TIMESTAMP WITH TIME ZONE, updated_at TIMESTAMP WITH TIME ZONE)");
    }

    protected void vider() {
        jdbc().update("DELETE FROM dossier_medical_patient WHERE center_id IN (?, ?)", CENTRE_A.valeur(), CENTRE_B.valeur());
    }

    @Override
    protected DossiersMedicauxDeBase dossiers() {
        return new DossiersMedicauxDeBaseJdbc(jdbc());
    }

    @Test
    void une_ligne_sans_aucune_date_est_lue_sans_erreur() {
        jdbc().update("INSERT INTO dossier_medical_patient (id, patient_id, center_id) VALUES (?,?,?)", UUID.randomUUID(), PATIENT.valeur(), CENTRE_A.valeur());
        var relu = dossiers().duPatient(CENTRE_A, PATIENT).orElseThrow();
        assertThat(relu.creeLe()).isNull();
        assertThat(relu.modifieLe()).isNull();
        assertThat(relu.nephropathieInitiale()).isNull();
    }
}
