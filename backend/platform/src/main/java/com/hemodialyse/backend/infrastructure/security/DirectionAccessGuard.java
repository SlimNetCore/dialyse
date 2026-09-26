package com.hemodialyse.backend.infrastructure.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Résout la société d'une session « direction » — et la <b>revérifie à chaque requête</b> : compte actif, rôle
 * DIRECTION, rattachement à cette société, société active. Le jeton seul ne suffit pas : un compte désactivé ou
 * détaché perd l'accès immédiatement, sans attendre l'expiration de son jeton.
 * <p>
 * Le périmètre ne vient jamais de la requête (aucun {@code societeId} en paramètre) : c'est celui du jeton.
 */
@Component
public class DirectionAccessGuard {

    private final JdbcTemplate jdbc;

    public DirectionAccessGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @return la société de la direction connectée ; @throws AccessDeniedException sinon
     */
    public UUID requireSociete() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)
                || authentication.getAuthorities().stream().noneMatch(a -> "ROLE_DIRECTION".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Accès réservé à la direction");
        }
        if (principal.getSocieteId() == null || principal.getSocieteId().isBlank()) {
            throw new AccessDeniedException("Session sans société");
        }
        UUID userId = UUID.fromString(principal.getId());
        UUID societeId = UUID.fromString(principal.getSocieteId());
        Integer ok = jdbc.queryForObject(
                "SELECT COUNT(1) FROM app_user u "
                        + "INNER JOIN app_user_societe us ON us.user_id = u.id "
                        + "INNER JOIN societes s ON s.id = us.societe_id "
                        + "WHERE u.id = ? AND u.active = TRUE AND us.societe_id = ? AND s.actif = TRUE",
                Integer.class, userId, societeId);
        if (ok == null || ok == 0) {
            throw new AccessDeniedException("Accès à cette société refusé");
        }
        return societeId;
    }
}
