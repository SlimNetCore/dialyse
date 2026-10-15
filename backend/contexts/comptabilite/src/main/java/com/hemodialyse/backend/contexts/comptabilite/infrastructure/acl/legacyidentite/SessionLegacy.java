package com.hemodialyse.backend.contexts.comptabilite.infrastructure.acl.legacyidentite;

import com.hemodialyse.backend.contexts.comptabilite.application.SessionCourante;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

/**
 * ACL vers la session legacy (contexte Identité et accès, ADR 0008) : reproduit la garde de centre du legacy
 * ({@code CenterAccessGuard.requireCenter}, RG-CPT-016). Le principal authentifié du legacy ({@code UserPrincipal}, module
 * platform) n'est pas visible du module (aucune dépendance Maven vers le legacy) : seul son accesseur {@code getCenterId()}
 * (chaîne UUID) est lu par réflexion. Retirée quand le contexte Identité et accès sera extrait.
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

        // Le SUPERADMIN (éditeur de la solution) est transverse aux centres.
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
