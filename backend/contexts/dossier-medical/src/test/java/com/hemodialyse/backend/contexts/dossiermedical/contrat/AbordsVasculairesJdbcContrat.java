package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordsVasculaires;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.AbordsVasculairesJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat des abords vasculaires sur la table réelle (H2 et PostgreSQL 16 : sous-classes). */
abstract class AbordsVasculairesJdbcContrat extends AbordsVasculairesContrat {
    protected abstract JdbcTemplate jdbc();

    protected void vider() {
        for (Object centre : new Object[]{CENTRE_A.valeur(), CENTRE_B.valeur()}) {
            jdbc().update("DELETE FROM abords_vasculaires WHERE center_id = ?", centre);
        }
    }

    @Override
    protected AbordsVasculaires abords() {
        return new AbordsVasculairesJdbc(jdbc());
    }

    @Test
    void la_table_garde_ses_colonnes_et_aucune_colonne_n_est_ajoutee() {
        List<String> colonnes = jdbc().queryForList("SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = 'abords_vasculaires'", String.class);
        assertThat(colonnes).containsExactlyInAnyOrder("id", "actif", "center_id", "complications", "cote", "created_at", "date_creation", "date_fin",
                "localisation", "patient_id", "type_abord");
    }

    @Test
    void un_type_vide_est_accepte_comme_le_legacy() {
        var vide = com.hemodialyse.backend.contexts.dossiermedical.domain.AbordVasculaire.enregistrer(java.util.UUID.randomUUID(), PATIENT, CENTRE_A, "",
                null, null, null, null, null, null, T0);
        abords().ajouter(vide);
        assertThat(abords().trouverEnVerrouillant(CENTRE_A, PATIENT, vide.id()).orElseThrow().type()).isEmpty();
    }
}
