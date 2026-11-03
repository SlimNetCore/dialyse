package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.legacygmao;

import com.hemodialyse.backend.contexts.patientcouverture.application.GenerateursDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

/** ACL de lecture du générateur affecté : l'équipement GMAO ({@code gmao_equipements}) du centre. */
public class GenerateursDuCentreJdbc implements GenerateursDuCentre {

    private final JdbcTemplate jdbc;

    public GenerateursDuCentreJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Generateur> trouver(CentreId centre, UUID id) {
        // Un équipement supprimé reste décrit (dernier code, marque et statut connus) : c'est ce que rend la fiche actuelle, dont
        // le mapping de lecture ne filtre pas la suppression.
        return jdbc.query("SELECT id, code, fabricant, statut FROM gmao_equipements WHERE centre_id = ? AND id = ?",
                (rs, n) -> new Generateur(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("fabricant"),
                        rs.getString("statut")), centre.valeur(), id).stream().findFirst();
    }
}
