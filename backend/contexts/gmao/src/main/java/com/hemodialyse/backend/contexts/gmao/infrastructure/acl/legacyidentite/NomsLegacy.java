package com.hemodialyse.backend.contexts.gmao.infrastructure.acl.legacyidentite;

import com.hemodialyse.backend.contexts.gmao.application.intervention.NomsDesUtilisateurs;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

/**
 * ACL vers le contexte Identité et accès (legacy) : le nom d'un utilisateur est son nom complet, à défaut son identifiant de
 * connexion (table {@code app_user}, lecture seule). Retirée quand ce contexte sera extrait.
 */
public class NomsLegacy implements NomsDesUtilisateurs {

    private final JdbcTemplate jdbc;

    public NomsLegacy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> de(UtilisateurId utilisateur) {
        List<String> noms = jdbc.queryForList("SELECT COALESCE(full_name, username) FROM app_user WHERE id = ?", String.class,
                utilisateur.valeur());
        return noms.isEmpty() ? Optional.empty() : Optional.ofNullable(noms.get(0));
    }
}
