package com.hemodialyse.backend.infrastructure.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Construit l'authentification Spring Security à partir d'un jeton d'accès valide. Partagé par le filtre HTTP et
 * par l'établissement de la connexion WebSocket, pour que les deux appliquent exactement les mêmes règles.
 */
@Component
public class JwtAuthenticationFactory {

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFactory(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    /**
     * @return l'authentification du porteur du jeton, ou vide si le jeton est absent ou invalide
     */
    public Optional<Authentication> fromToken(String token) {
        if (!StringUtils.hasText(token) || !tokenProvider.validateToken(token)) {
            return Optional.empty();
        }
        List<SimpleGrantedAuthority> authorities = tokenProvider.getRolesFromToken(token).stream()
                .map(r -> new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r))
                .toList();
        UserPrincipal principal = new UserPrincipal(tokenProvider.getUserIdFromToken(token),
                tokenProvider.getCenterIdFromToken(token), tokenProvider.getUsernameFromToken(token), "",
                authorities, true, tokenProvider.getSocieteIdFromToken(token));
        return Optional.of(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
