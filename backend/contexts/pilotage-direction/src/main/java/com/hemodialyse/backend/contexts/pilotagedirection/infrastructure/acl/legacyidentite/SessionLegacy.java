package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.legacyidentite;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesSocietes;
import com.hemodialyse.backend.contexts.pilotagedirection.application.HabilitationDeLaDirection;
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
public final class SessionLegacy implements SessionDeLaDirection, HabilitationDeLaDirection {

    private final JdbcTemplate jdbc;
    private final AnnuaireDesSocietes societes;

    public SessionLegacy(JdbcTemplate jdbc, AnnuaireDesSocietes societes) {
        this.jdbc = jdbc;
        this.societes = societes;
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
        if (!estHabilite(utilisateur, societeId)) {
            throw new AccessDeniedException("Accès à cette société refusé");
        }
        return societeId;
    }

    /** Compte actif, rattaché à la société, société active : revérifié en base (PL10b2, remplace {@code DirectionAccessGuard.verifyMember}). */
    @Override
    public boolean estHabilite(UUID utilisateur, UUID societe) {
        Integer rattache = jdbc.queryForObject(
                "SELECT COUNT(1) FROM app_user u "
                        + "INNER JOIN app_user_societe us ON us.user_id = u.id "
                        + "WHERE u.id = ? AND u.active = TRUE AND us.societe_id = ?",
                Integer.class, utilisateur, societe);
        // L'état de la société est lu dans le langage publié d'Organisation (IDN-lecture, ADR 0174) : plus de lecture de `societes`.
        return rattache != null && rattache > 0 && societes.societesActives().contains(societe);
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
