package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuReferentielDesInfirmiers;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verrou du référentiel des infirmiers (ADR 0056) : verrou de la ligne du centre ({@code centers}), pris par une mise à jour sans
 * changement ({@code UPDATE centers SET id = id}). Sur PostgreSQL c'est un verrou {@code FOR NO KEY UPDATE} : il exclut les autres
 * écritures du référentiel du même centre sans bloquer les insertions des tables qui référencent le centre (clés étrangères,
 * verrou {@code FOR KEY SHARE}), contrairement à {@code SELECT … FOR UPDATE}. Le refus de verrou devient une écriture concurrente.
 * Un centre sans ligne (jeux de test) n'a rien à verrouiller : la contrainte d'unicité reste le dernier garde-fou.
 */
public class VerrouDuReferentielDesInfirmiersJdbc implements VerrouDuReferentielDesInfirmiers {
    private final JdbcTemplate jdbc;

    public VerrouDuReferentielDesInfirmiersJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void prendre(CentreId centre) {
        RefusDeVerrou.verrouillant(() -> jdbc.update("UPDATE centers SET id = id WHERE id = ?", centre.valeur()));
    }
}
