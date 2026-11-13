package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.legacyidentite;

import com.hemodialyse.backend.contexts.dossiermedical.application.AccesRefuse;
import com.hemodialyse.backend.contexts.dossiermedical.application.ProfilDeLaSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.stream.Collectors;

/** ACL vers la session legacy (Identité et accès) : les autorités du jeton, ou {@link AccesRefuse} (403) sans session authentifiée. */
public class ProfilLegacy implements ProfilDeLaSession {

    @Override
    public Collection<String> roles() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || !authentification.isAuthenticated() || authentification.getPrincipal() == null) {
            throw new AccesRefuse("Contexte d'authentification invalide : centre de rattachement introuvable");
        }
        return authentification.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toUnmodifiableSet());
    }
}
