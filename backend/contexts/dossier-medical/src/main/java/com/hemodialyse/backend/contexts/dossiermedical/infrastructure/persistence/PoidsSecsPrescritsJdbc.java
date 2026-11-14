package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.PoidsSecsPrescritsPublies;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Poids secs cibles prescrits (LEC-PLN-GMAO c, ADR 0180) sur la table réelle {@code prescriptions_medicales}. Ordre repris MOT POUR MOT de
 * l'ancienne sous-requête de la Séance ({@code StatistiquesDesSeancesJdbc}, L8) : date décroissante, mise à jour décroissante inconnue en
 * dernier, identifiant décroissant.
 */
public class PoidsSecsPrescritsJdbc implements PoidsSecsPrescritsPublies {

    private final JdbcTemplate jdbc;

    public PoidsSecsPrescritsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PoidsSecPrescrit> poidsSecsCiblesPrescrits(UUID centre, UUID patient) {
        return jdbc.query("SELECT pm.date_prescription, pm.poids_sec_cible_kg FROM prescriptions_medicales pm "
                        + "WHERE pm.center_id = ? AND pm.patient_id = ? AND pm.poids_sec_cible_kg IS NOT NULL "
                        + "ORDER BY pm.date_prescription DESC, pm.updated_at DESC NULLS LAST, pm.id DESC",
                (rs, n) -> new PoidsSecPrescrit(rs.getDate("date_prescription").toLocalDate(), rs.getBigDecimal("poids_sec_cible_kg")),
                centre, patient);
    }
}
