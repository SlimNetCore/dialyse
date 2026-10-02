package com.hemodialyse.backend.infrastructure.security;

import com.hemodialyse.backend.application.auth.ChangementMotDePasseService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Bloque l'API tant qu'un utilisateur connecté avec un mot de passe temporaire ne l'a pas remplacé : toute route
 * {@code /api/**} hors {@code /api/v1/auth/**} (session, déconnexion, changement de mot de passe) répond 403 avec le
 * code {@value #CODE}. S'exécute après l'authentification JWT.
 */
@Component
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    public static final String CODE = "PASSWORD_CHANGE_REQUIRED";
    private static final String PREFIXE_API = "/api/";
    private static final String PREFIXE_AUTH = "/api/v1/auth/";

    private final ChangementMotDePasseService motsDePasse;

    /**
     * Le service est résolu à la première requête : il dépend de l'encodeur de mots de passe défini dans
     * {@link SecurityConfig}, qui dépend lui-même de ce filtre (sinon dépendance circulaire au démarrage).
     */
    public PasswordChangeRequiredFilter(@Lazy ChangementMotDePasseService motsDePasse) {
        this.motsDePasse = motsDePasse;
    }

    private static boolean concerne(HttpServletRequest request) {
        String chemin = request.getRequestURI();
        return chemin.startsWith(PREFIXE_API) && !chemin.startsWith(PREFIXE_AUTH)
                && !HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (concerne(request) && doitChanger()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"status\":403,\"code\":\"" + CODE
                    + "\",\"detail\":\"Vous devez remplacer votre mot de passe temporaire avant de continuer.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean doitChanger() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal) || principal.getId() == null) {
            return false;
        }
        try {
            return motsDePasse.doitChanger(UUID.fromString(principal.getId()));
        } catch (IllegalArgumentException e) {
            return false;   // identifiant non UUID (jeton de test ou ancien format) : pas de contrôle
        }
    }
}
