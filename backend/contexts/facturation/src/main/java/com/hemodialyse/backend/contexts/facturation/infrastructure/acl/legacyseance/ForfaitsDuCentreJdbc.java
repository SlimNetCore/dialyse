package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitsDuCentre;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

/**
 * ACL sur le référentiel des forfaits (table {@code forfait}, module Référentiel) : un forfait du centre, avec son libellé et
 * son prix hors taxe.
 */
public class ForfaitsDuCentreJdbc implements ForfaitsDuCentre {

    private final JdbcTemplate jdbc;

    public ForfaitsDuCentreJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ForfaitApplique> forfait(CentreId centre, UUID forfait) {
        return jdbc.query("SELECT id, libelle, prix FROM forfait WHERE center_id = ? AND id = ?",
                (rs, ligne) -> new ForfaitApplique(rs.getObject("id", UUID.class), rs.getString("libelle"),
                        rs.getBigDecimal("prix")),
                centre.valeur(), forfait).stream().findFirst();
    }
}
