package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.GenerateursFournis;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Fournit à la Séance le langage publié {@link GenerateursFournis} : le générateur est un équipement de la GMAO (source de vérité
 * unique) ; lecture du code, du fabricant et du statut, bornée au centre, hors équipement supprimé.
 */
@Component
public class GenerateursFournisLegacy implements GenerateursFournis {

    private final JdbcTemplate jdbc;

    public GenerateursFournisLegacy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Generateur> duCentre(UUID centre, UUID equipement) {
        return jdbc.query("SELECT id, code, fabricant, statut FROM gmao_equipements WHERE centre_id = ? AND id = ? AND deleted_at IS NULL",
                (rs, i) -> new Generateur(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("fabricant"),
                        rs.getString("statut")),
                centre, equipement).stream().findFirst();
    }
}
