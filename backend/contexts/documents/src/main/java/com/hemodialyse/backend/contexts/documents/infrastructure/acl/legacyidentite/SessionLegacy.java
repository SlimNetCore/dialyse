package com.hemodialyse.backend.contexts.documents.infrastructure.acl.legacyidentite;

import com.hemodialyse.backend.contexts.documents.application.AccesRefuse;
import com.hemodialyse.backend.contexts.documents.application.SessionCourante;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

/**
 * ACL vers la session legacy (Identité et accès) : même garde de centre et mêmes motifs de refus que {@code CenterAccessGuard}
 * (KB-DOC1-1). Le principal du legacy n'est pas visible du module (aucune dépendance Maven vers la plateforme) : seul son accesseur
 * {@code getCenterId()} est lu par réflexion. Retirée quand Identité et accès sera extrait.
 */
public class SessionLegacy implements SessionCourante {

    private static final String ROLE_SUPERADMIN = "ROLE_SUPERADMIN";

    @Override
    public CentreId exigerLeCentre(UUID centreDemande) {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || !authentification.isAuthenticated() || authentification.getPrincipal() == null
                || !possede(authentification.getPrincipal(), "getCenterId")) {
            throw new AccesRefuse("Contexte d'authentification invalide : centre de rattachement introuvable");
        }
        UUID centreDeSession = lireLeCentre(authentification.getPrincipal());
        boolean transverse = authentification.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(ROLE_SUPERADMIN::equals);
        if (transverse) {
            UUID retenu = centreDemande != null ? centreDemande : centreDeSession;
            if (retenu == null) throw new AccesRefuse("centerId requis pour un accès transverse");
            return new CentreId(retenu);
        }
        if (centreDeSession == null) throw new AccesRefuse("Session sans centre de rattachement");
        if (centreDemande != null && !centreDemande.equals(centreDeSession)) {
            throw new AccesRefuse("Accès refusé : le centre demandé ne correspond pas à la session");
        }
        return new CentreId(centreDeSession);
    }

    private static boolean possede(Object principal, String accesseur) {
        try {
            principal.getClass().getMethod(accesseur);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static UUID lireLeCentre(Object principal) {
        String brut = lire(principal, "getCenterId");
        if (brut == null || brut.isBlank()) return null;
        try {
            return UUID.fromString(brut);
        } catch (IllegalArgumentException e) {
            throw new AccesRefuse("Centre de rattachement illisible dans la session");
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
