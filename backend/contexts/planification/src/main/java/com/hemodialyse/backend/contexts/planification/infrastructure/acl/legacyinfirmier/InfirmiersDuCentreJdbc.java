package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyinfirmier;

import com.hemodialyse.backend.contexts.planification.application.InfirmiersDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * ACL vers le référentiel des infirmiers (table {@code infirmier}, encore dans le legacy).
 */
public class InfirmiersDuCentreJdbc implements InfirmiersDuCentre {

    private final JdbcTemplate jdbc;

    public InfirmiersDuCentreJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Situation situation(CentreId centre, InfirmierId infirmier) {
        List<Boolean> actifs = jdbc.query("SELECT actif FROM infirmier WHERE center_id = ? AND id = ?",
                (rs, i) -> rs.getBoolean("actif"), centre.valeur(), infirmier.valeur());
        if (actifs.isEmpty()) return Situation.INTROUVABLE;
        return actifs.getFirst() ? Situation.ACTIF : Situation.INACTIF;
    }
}
