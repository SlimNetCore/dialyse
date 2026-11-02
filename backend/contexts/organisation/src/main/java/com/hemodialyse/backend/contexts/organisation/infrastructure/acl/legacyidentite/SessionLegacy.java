package com.hemodialyse.backend.contexts.organisation.infrastructure.acl.legacyidentite;

import com.hemodialyse.backend.contexts.organisation.application.SessionCourante;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

/**
 * ACL vers la session de la plateforme (contexte Identité et accès, ADR 0008) : même garde de centre que
 * {@code CenterAccessGuard}. Le principal authentifié n'est pas visible du module (aucune dépendance Maven vers la
 * plateforme) : seul son accesseur {@code getCenterId()} (chaîne UUID) est lu par réflexion. Le centre vient de la
 * session, jamais seulement du client.
 * <p>
 * Le SUPERADMIN est transverse aux centres, comme dans le legacy ; mais le {@code RoleScopeFilter} de la plateforme ne lui ouvre,
 * sous {@code /api/v1/organisation}, que {@code societes} et {@code centres} (décision du 2026-11-02) : seule la lecture
 * {@code /centres/{id}/parametres} passe donc par cette branche ; les routes de salles, créneaux et référentiels lui répondent
 * 403 {@code ROLE_SCOPE} avant d'atteindre ce code.
 */
public class SessionLegacy implements SessionCourante {

    private static final String ROLE_SUPERADMIN = "ROLE_SUPERADMIN";

    private static boolean possedeUnCentre(Object principal) {
        try {
            principal.getClass().getMethod("getCenterId");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static UUID lireLeCentre(Object principal) {
        String brut;
        try {
            brut = (String) principal.getClass().getMethod("getCenterId").invoke(principal);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Principal de session inattendu : " + principal.getClass().getName(), e);
        }
        if (brut == null || brut.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(brut);
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("Centre de rattachement illisible dans la session");
        }
    }

    @Override
    public CentreId centre(UUID centreDemande) {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || !authentification.isAuthenticated() || authentification.getPrincipal() == null
                || !possedeUnCentre(authentification.getPrincipal())) {
            throw new AccessDeniedException("Contexte d'authentification invalide : centre de rattachement introuvable");
        }
        UUID centreDeSession = lireLeCentre(authentification.getPrincipal());
        if (authentification.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(ROLE_SUPERADMIN::equals)) {
            UUID retenu = centreDemande != null ? centreDemande : centreDeSession;
            if (retenu == null) {
                throw new AccessDeniedException("centerId requis pour un accès transverse");
            }
            return new CentreId(retenu);
        }
        if (centreDeSession == null) {
            throw new AccessDeniedException("Session sans centre de rattachement");
        }
        if (centreDemande != null && !centreDemande.equals(centreDeSession)) {
            throw new AccessDeniedException("Accès refusé : le centre demandé ne correspond pas à la session");
        }
        return new CentreId(centreDeSession);
    }
}
