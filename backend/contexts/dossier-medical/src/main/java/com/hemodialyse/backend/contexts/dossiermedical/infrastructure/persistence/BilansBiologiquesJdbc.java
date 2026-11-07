package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilansBiologiques;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

/** Lecture des résultats d'analyses (table {@code resultats_analyses}), seules les colonnes du suivi de l'anémie. */
public class BilansBiologiquesJdbc implements BilansBiologiques {

    private final JdbcTemplate jdbc;

    public BilansBiologiquesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<BilanBiologique> lesPlusRecents(CentreId centre, PatientId patient, int nombre) {
        return jdbc.query("SELECT date_prelevement, hb_g_dl, ferritine_ng_ml, cstf_pct, albumine_g_dl, kt_v_mensuel FROM resultats_analyses "
                + "WHERE center_id = ? AND patient_id = ? ORDER BY date_prelevement DESC, created_at DESC, id DESC LIMIT ?",
                (rs, i) -> new BilanBiologique(rs.getObject("date_prelevement", LocalDate.class), rs.getBigDecimal("hb_g_dl"),
                        rs.getBigDecimal("ferritine_ng_ml"), rs.getBigDecimal("cstf_pct"), rs.getBigDecimal("albumine_g_dl"),
                        rs.getBigDecimal("kt_v_mensuel")), centre.valeur(), patient.valeur(), nombre);
    }
}
