package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ReferentielDeForfaitsFourni;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Fournit à la Séance le langage publié {@link ReferentielDeForfaitsFourni} : même requête que l'ancien {@code SeanceForfaitCatalogAdapter}
 * (forfait du centre par identifiant, code, libellé, prix tels quels). SELECT seul.
 */
@Component
public class ReferentielDeForfaitsFourniLegacy implements ReferentielDeForfaitsFourni {

    private final JdbcTemplate jdbc;

    public ReferentielDeForfaitsFourniLegacy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Forfait> forfait(UUID centre, UUID forfait) {
        return jdbc.query("SELECT id, code, libelle, prix FROM forfait WHERE center_id = ? AND id = ?",
                (rs, i) -> new Forfait(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("libelle"),
                        rs.getBigDecimal("prix")), centre, forfait).stream().findFirst();
    }
}
