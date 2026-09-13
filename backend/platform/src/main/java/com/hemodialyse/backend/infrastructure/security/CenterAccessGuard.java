package com.hemodialyse.backend.infrastructure.security;

import com.hemodialyse.backend.domain.shared.TenantScope;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Confronte le {@code centerId} fourni par le client au centre porté par le JWT (AGENTS.md §2).
 * <p>
 * Le reste de l'application fait confiance au {@code centerId} de la requête : l'isolation
 * multi-centres n'y repose que sur le filtrage en base. Sur le dossier médical — donnée de
 * santé — cette confiance n'est pas acceptable : sans ce contrôle, un médecin authentifié
 * lirait le dossier d'un patient d'un autre centre en changeant un UUID dans l'URL.
 * <p>
 * Matérialise enfin {@link TenantScope}, jusqu'ici défini dans le shared-kernel sans aucun usage.
 * Volontairement limité aux contrôleurs du dossier médical pour borner le rayon d'impact ;
 * les autres modules pourront l'adopter progressivement.
 */
@Component
public class CenterAccessGuard {

    private static final String ROLE_SUPERADMIN = "ROLE_SUPERADMIN";

    /**
     * Construit le périmètre du porteur du jeton courant.
     *
     * @throws AccessDeniedException si le contexte de sécurité ne porte pas de {@link UserPrincipal}
     */
    public TenantScope currentScope() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AccessDeniedException("Contexte d'authentification invalide : centre de rattachement introuvable");
        }
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
        return new TenantScope(parseCenterId(principal.getCenterId()), principal.getId(), roles);
    }

    /**
     * Résout le centre à utiliser pour la requête courante.
     *
     * @param requestedCenterId centre demandé par le client ({@code null} = « celui de ma session »)
     * @return le centre du jeton, seule valeur digne de confiance
     * @throws AccessDeniedException si le client réclame un centre qui n'est pas le sien
     */
    public CenterId requireCenter(UUID requestedCenterId) {
        TenantScope scope = currentScope();

        // Le SUPERADMIN (éditeur de la solution) est transverse aux centres, comme dans LicenseEnforcementFilter.
        if (scope.roles().contains(ROLE_SUPERADMIN)) {
            UUID resolved = requestedCenterId != null ? requestedCenterId : scope.centerId();
            if (resolved == null) {
                throw new AccessDeniedException("centerId requis pour un accès transverse");
            }
            return CenterId.of(resolved);
        }

        UUID tokenCenterId = scope.centerId();
        if (tokenCenterId == null) {
            throw new AccessDeniedException("Session sans centre de rattachement");
        }
        if (requestedCenterId != null && !requestedCenterId.equals(tokenCenterId)) {
            throw new AccessDeniedException("Accès refusé : le centre demandé ne correspond pas à la session");
        }
        return CenterId.of(tokenCenterId);
    }

    private UUID parseCenterId(String rawCenterId) {
        if (rawCenterId == null || rawCenterId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawCenterId);
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("Centre de rattachement illisible dans la session");
        }
    }
}
