package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyidentite;

import com.hemodialyse.backend.contexts.seance.application.AccesRefuse;
import com.hemodialyse.backend.contexts.seance.application.SessionCourante;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

/**
 * ACL vers la session legacy (Identité et accès) : même garde de centre et mêmes motifs de refus que {@code CenterAccessGuard}. Le
 * principal du legacy n'est pas visible du module (aucune dépendance Maven vers le legacy) : seul son accesseur {@code getCenterId()} est
 * lu par réflexion. Toute session invalide devient {@link AccesRefuse} (403). Auteur : le nom de la session, « system » sans session
 * (comme {@code CurrentUser}). Retirée quand Identité et accès sera extrait.
 */
public class SessionLegacy implements SessionCourante {

    private static final String ROLE_SUPERADMIN = "ROLE_SUPERADMIN";

    @Override
    public CentreId centre(UUID centreDemande) {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || !authentification.isAuthenticated() || authentification.getPrincipal() == null
                || !possedeUnCentre(authentification.getPrincipal())) {
            throw new AccesRefuse("Contexte d'authentification invalide : centre de rattachement introuvable");
        }
        UUID centreDeSession = lireLeCentre(authentification.getPrincipal());
        if (possede(authentification, ROLE_SUPERADMIN)) {
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

    @Override
    public String utilisateur() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a.getName() == null || a.getName().isBlank()) return "system";
        return a.getName();
    }

    @Override
    public boolean estAdministrateur() {
        return possede(SecurityContextHolder.getContext().getAuthentication(), "ROLE_ADMIN");
    }

    @Override
    public boolean valideAuScan() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return possede(a, "ROLE_ADMIN") || possede(a, "ROLE_INFIRMIER");
    }

    @Override
    public boolean peutLireLeVoletMedical() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return possede(a, "ROLE_ADMIN") || possede(a, "ROLE_MEDECIN") || possede(a, "ROLE_INFIRMIER");
    }

    private static boolean possede(Authentication a, String role) {
        if (a == null || !a.isAuthenticated()) return false;
        return a.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(role::equals);
    }

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
        if (brut == null || brut.isBlank()) return null;
        try {
            return UUID.fromString(brut);
        } catch (IllegalArgumentException e) {
            throw new AccesRefuse("Centre de rattachement illisible dans la session");
        }
    }
}
