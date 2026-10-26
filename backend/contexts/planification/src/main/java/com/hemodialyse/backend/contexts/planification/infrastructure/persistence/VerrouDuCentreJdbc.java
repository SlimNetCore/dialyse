package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuCentre;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verrou du centre (ADR 0049) : {@code SELECT … FOR UPDATE} de la ligne {@code planning_parametres} du centre, s'il y en a une.
 * Ne crée rien. Le refus de verrou devient une écriture concurrente.
 */
public class VerrouDuCentreJdbc implements VerrouDuCentre {

    private final JdbcTemplate jdbc;

    public VerrouDuCentreJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void prendre(CentreId centre) {
        RefusDeVerrou.verrouillant(() -> jdbc.queryForList(
                "SELECT center_id FROM planning_parametres WHERE center_id = ? FOR UPDATE", centre.valeur()));
    }
}
