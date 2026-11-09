package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.VerrouDuCentre;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Verrou de la ligne d'un centre. SQL repris à l'identique de {@code InventairesJdbc.verrouillerLaPeriode} (Stock, retiré au lot B de O4,
 * ADR 0108).
 */
public class VerrouDuCentreJdbc implements VerrouDuCentre {

    private final JdbcTemplate jdbc;

    public VerrouDuCentreJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void verrouiller(UUID centre) {
        jdbc.queryForList("SELECT id FROM centers WHERE id = ? FOR UPDATE", UUID.class, centre);
    }
}
