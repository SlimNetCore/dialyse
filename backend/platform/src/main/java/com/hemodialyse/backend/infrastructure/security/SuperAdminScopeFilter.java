package com.hemodialyse.backend.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Cantonne le compte {@code SUPERADMIN} — le propriétaire de l'application — à ses seules responsabilités :
 * la gestion des sociétés (et de leurs centres) et des licences.
 * <p>
 * Le propriétaire n'a aucune raison d'accéder aux données d'un centre (patients, séances, dossiers médicaux, stock,
 * facturation…), et ne doit pas pouvoir le faire : le secret médical et l'isolation entre clients sont garantis
 * par le serveur, pas seulement par le menu de l'interface. Toute autre route {@code /api/v1/**} répond 403 pour
 * ce rôle, même si un contrôleur l'aurait autorisé.
 */
@Component
public class SuperAdminScopeFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1/";

    /**
     * Routes que le propriétaire peut appeler.
     */
    private static final String[] ALLOWED_PREFIXES = {
            "/api/v1/societes",
            "/api/v1/licenses",
            "/api/v1/auth/",
            "/api/v1/system/",
    };

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith(API_PREFIX) || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        for (String prefix : ALLOWED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean superAdmin = auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream().anyMatch(a -> "ROLE_SUPERADMIN".equals(a.getAuthority()));
        if (!superAdmin) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"code":"SUPERADMIN_SCOPE","title":"Accès refusé","detail":"Le compte propriétaire n'accède qu'aux sociétés et aux licences."}""");
    }
}
