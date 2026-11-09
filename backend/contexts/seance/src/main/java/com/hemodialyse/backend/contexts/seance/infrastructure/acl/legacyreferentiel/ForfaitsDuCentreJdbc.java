package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyreferentiel;

import com.hemodialyse.backend.contexts.seance.domain.ForfaitDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitId;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

/**
 * Référentiel des forfaits du centre (SE7f, ADR 0100) : requête reprise MOT POUR MOT de {@code ReferentielDeForfaitsFourniLegacy}
 * (plateforme, retiré) : forfait du centre par identifiant, code, libellé (devenu le nom) et prix tels quels, SELECT seul sur
 * {@code forfait} (référentiel d'administration, lecture transitoire).
 */
public class ForfaitsDuCentreJdbc implements ForfaitsDuCentre {

    private final JdbcTemplate jdbc;

    public ForfaitsDuCentreJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ForfaitDuCentre> trouver(CentreId centre, ForfaitId forfait) {
        return jdbc.query("SELECT id, code, libelle, prix FROM forfait WHERE center_id = ? AND id = ?",
                (rs, i) -> new ForfaitDuCentre(new ForfaitId(rs.getObject("id", UUID.class)), rs.getString("code"), rs.getString("libelle"),
                        rs.getBigDecimal("prix")), centre.valeur(), forfait.valeur()).stream().findFirst();
    }
}
