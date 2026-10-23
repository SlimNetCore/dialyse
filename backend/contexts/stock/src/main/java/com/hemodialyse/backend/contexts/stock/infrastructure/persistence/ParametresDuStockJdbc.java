package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.parametres.ParametresDuStock;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Table {@code stock_parametres} (migration V45) : une ligne par centre, créée au premier réglage.
 */
public class ParametresDuStockJdbc implements ParametresDuStock {
    private final JdbcTemplate jdbc;

    public ParametresDuStockJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean sortiePourSeancesActivee(CentreId centre) {
        return jdbc.queryForList("SELECT sortie_pour_seances_activee FROM stock_parametres WHERE center_id = ?", Boolean.class,
                centre.valeur()).stream().anyMatch(Boolean.TRUE::equals);
    }

    @Override
    public void definirLaSortiePourSeances(CentreId centre, boolean activee) {
        if (jdbc.update("UPDATE stock_parametres SET sortie_pour_seances_activee = ? WHERE center_id = ?", activee, centre.valeur()) == 0) {
            jdbc.update("INSERT INTO stock_parametres (center_id, sortie_pour_seances_activee) VALUES (?, ?)", centre.valeur(), activee);
        }
    }
}