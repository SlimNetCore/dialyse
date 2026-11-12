package com.hemodialyse.backend.contexts.notifications.infrastructure.acl.legacyidentite;

import com.hemodialyse.backend.contexts.notifications.application.AccesRefuse;
import com.hemodialyse.backend.contexts.notifications.application.Session;
import com.hemodialyse.backend.contexts.notifications.application.SessionCourante;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ACL vers la session legacy (Identité et accès) : même garde de centre et mêmes motifs de refus que {@code CenterAccessGuard}. Le principal
 * du legacy n'est pas visible du module (aucune dépendance Maven vers la plateforme) : seuls ses accesseurs {@code getCenterId()} et
 * {@code getId()} sont lus par réflexion. Les rôles sont rendus sans préfixe {@code ROLE_}. Retirée quand Identité et accès sera extrait.
 */
public class SessionLegacy implements SessionCourante {

    private static final String ROLE_SUPERADMIN = "ROLE_SUPERADMIN";
    private static final String PREFIXE = "ROLE_";

    @Override
    public Session ouvrir(UUID centreDemande) {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || !authentification.isAuthenticated() || authentification.getPrincipal() == null
                || !possede(authentification.getPrincipal(), "getCenterId")) {
            throw new AccesRefuse("Contexte d'authentification invalide : centre de rattachement introuvable");
        }
        Object principal = authentification.getPrincipal();
        UUID centreDeSession = lireLeCentre(principal);
        Set<String> autorites = authentification.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> roles = autorites.stream().map(r -> r.startsWith(PREFIXE) ? r.substring(PREFIXE.length()) : r)
                .collect(Collectors.toUnmodifiableSet());
        return new Session(retenir(centreDemande, centreDeSession, autorites.contains(ROLE_SUPERADMIN)), lire(principal, "getId"), roles);
    }

    private static CentreId retenir(UUID centreDemande, UUID centreDeSession, boolean transverse) {
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
