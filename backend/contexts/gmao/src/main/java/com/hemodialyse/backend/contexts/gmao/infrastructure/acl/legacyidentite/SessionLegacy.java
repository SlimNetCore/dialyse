package com.hemodialyse.backend.contexts.gmao.infrastructure.acl.legacyidentite;

import com.hemodialyse.backend.contexts.gmao.application.SessionCourante;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

/**
 * ACL vers la session legacy (contexte Identité et accès, ADR 0008). Le principal authentifié du legacy
 * ({@code UserPrincipal}, module platform) n'est pas visible du module GMAO (aucune dépendance Maven vers le legacy) :
 * seuls ses accesseurs {@code getCenterId()} et {@code getId()} (chaînes UUID) sont lus par réflexion, et traduits en
 * {@link CentreId} / {@link UtilisateurId}. Retirée quand le contexte Identité et accès sera extrait.
 */
public class SessionLegacy implements SessionCourante {

    @Override
    public CentreId centre() {
        return new CentreId(UUID.fromString(lire("getCenterId")));
    }

    @Override
    public UtilisateurId utilisateur() {
        return new UtilisateurId(UUID.fromString(lire("getId")));
    }

    @Override
    public String identifiant() {
        return lire("getUsername");
    }

    private static String lire(String accesseur) {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || authentification.getPrincipal() == null) {
            throw new IllegalStateException("Aucun utilisateur authentifié");
        }
        Object principal = authentification.getPrincipal();
        try {
            return (String) principal.getClass().getMethod(accesseur).invoke(principal);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Principal de session inattendu : " + principal.getClass().getName(), e);
        }
    }
}
