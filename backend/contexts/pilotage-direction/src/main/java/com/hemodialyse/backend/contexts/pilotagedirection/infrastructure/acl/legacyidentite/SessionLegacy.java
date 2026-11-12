package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.legacyidentite;

import com.hemodialyse.backend.contexts.pilotagedirection.application.SessionDeLaDirection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

/**
 * ACL vers la session legacy (Identité et accès) : mêmes refus que la {@code DirectionAccessGuard} de la plateforme (rôle DIRECTION,
 * société du jeton, compte actif rattaché à une société active, revérifiés en base à chaque appel). Le principal du legacy n'est pas
 * visible du module : seuls ses accesseurs {@code getId()} et {@code getSocieteId()} sont lus par réflexion. Retirée quand Identité et
 * accès sera extrait.
 */
public final class SessionLegacy implements SessionDeLaDirection {

    private final JdbcTemplate jdbc;

    public SessionLegacy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID societe() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || authentification.getPrincipal() == null
                || authentification.getAuthorities().stream().noneMatch(a -> "ROLE_DIRECTION".equals(a.getAuthority()))
                || !possede(authentification.getPrincipal())) {
            throw new AccessDeniedException("Accès réservé à la direction");
        }
        Object principal = authentification.getPrincipal();
        String societe = lire(principal, "getSocieteId");
        if (societe == null || societe.isBlank()) {
            throw new AccessDeniedException("Session sans société");
        }
        UUID societeId;
        UUID utilisateur;
        try {
            societeId = UUID.fromString(societe);
            utilisateur = UUID.fromString(lire(principal, "getId"));
        } catch (IllegalArgumentException | NullPointerException e) {
            // KB-PL9-5 (message) corrigé : 400 sans le message technique « Invalid UUID string »
            throw new IllegalArgumentException("Jeton de session illisible");
        }
        Integer rattache = jdbc.queryForObject(
                "SELECT COUNT(1) FROM app_user u "
                        + "INNER JOIN app_user_societe us ON us.user_id = u.id "
                        + "INNER JOIN societes s ON s.id = us.societe_id "
                        + "WHERE u.id = ? AND u.active = TRUE AND us.societe_id = ? AND s.actif = TRUE",
                Integer.class, utilisateur, societeId);
        if (rattache == null || rattache == 0) {
            throw new AccessDeniedException("Accès à cette société refusé");
        }
        return societeId;
    }

    private static boolean possede(Object principal) {
        try {
            principal.getClass().getMethod("getSocieteId");
            principal.getClass().getMethod("getId");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static String lire(Object principal, String accesseur) {
        try {
            return (String) principal.getClass().getMethod(accesseur).invoke(principal);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Principal de session inattendu : " + principal.getClass().getName(), e);
        }
    }
}
