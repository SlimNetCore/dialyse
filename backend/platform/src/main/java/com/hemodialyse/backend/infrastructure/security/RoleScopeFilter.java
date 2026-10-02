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
import java.util.List;
import java.util.Map;

/**
 * Cantonne les rôles « transverses » à leurs seules routes, côté serveur (et pas seulement dans le menu) :
 * <ul>
 *   <li>{@code SUPERADMIN} — le propriétaire de l'application — : gestion des sociétés (et de leurs comptes
 *       direction), des licences, et consultation du journal d'audit (toute la plateforme) uniquement ;</li>
 *   <li>{@code DIRECTION} — la direction d'une société — : tableaux de bord consolidés ({@code /api/v1/direction})
 *       uniquement, en lecture, sur des agrégats anonymes.</li>
 * </ul>
 * Ni l'un ni l'autre n'a de raison d'accéder aux données d'un centre (patients, séances, dossiers médicaux, stock,
 * facturation…) : le secret médical et l'isolation entre clients sont garantis par le serveur. Toute autre route
 * {@code /api/v1/**} répond 403 pour ces rôles, même si un contrôleur l'aurait autorisée.
 */
@Component
public class RoleScopeFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1/";

    /**
     * Routes communes : session (connexion, profil, déconnexion) et état du système.
     */
    private static final List<String> COMMON = List.of("/api/v1/auth/", "/api/v1/system/");

    /**
     * Routes autorisées par rôle restreint.
     */
    private static final Map<String, List<String>> ALLOWED_BY_ROLE = Map.of(
            "ROLE_SUPERADMIN", List.of("/api/v1/societes", "/api/v1/licenses", "/api/v1/audit"),
            "ROLE_DIRECTION", List.of("/api/v1/direction")
    );

    /**
     * Un compte cumulant plusieurs rôles restreints peut appeler l'union de leurs routes.
     */
    private static boolean isAllowed(String path, List<String> restrictedRoles) {
        for (String prefix : COMMON) {
            if (path.startsWith(prefix)) return true;
        }
        for (String role : restrictedRoles) {
            for (String prefix : ALLOWED_BY_ROLE.get(role)) {
                if (path.equals(prefix) || path.startsWith(prefix + "/")) return true;
            }
        }
        return false;
    }

    /**
     * Gestion du personnel et du planning du centre : interdite à l'infirmier « seul » (rôle INFIRMIER sans aucun autre
     * rôle de centre), qui ne dispose que de son propre planning et des séances.
     */
    private static final List<String> NURSE_DENIED = List.of("/api/v1/infirmiers", "/api/v1/planning");
    private static final String NURSE_OWN_PLANNING = "/api/v1/infirmiers/moi";
    private static final List<String> OTHER_CENTRE_ROLES = List.of("ROLE_ADMIN", "ROLE_MEDECIN", "ROLE_SECRETAIRE");

    private static boolean hasAuthority(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority()));
    }

    /**
     * Vrai pour un compte dont le seul rôle de centre est INFIRMIER.
     */
    private static boolean nurseOnly(Authentication auth) {
        return hasAuthority(auth, "ROLE_INFIRMIER")
                && OTHER_CENTRE_ROLES.stream().noneMatch(role -> hasAuthority(auth, role));
    }

    private static boolean deniedToNurse(String path) {
        if (path.equals(NURSE_OWN_PLANNING) || path.startsWith(NURSE_OWN_PLANNING + "/")) return false;
        return NURSE_DENIED.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith(API_PREFIX) || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }
        List<String> restrictedRoles = ALLOWED_BY_ROLE.keySet().stream()
                .filter(role -> auth.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority())))
                .toList();
        boolean allowed = restrictedRoles.isEmpty()
                ? !(nurseOnly(auth) && deniedToNurse(request.getRequestURI()))
                : isAllowed(request.getRequestURI(), restrictedRoles);
        if (allowed) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"code":"ROLE_SCOPE","title":"Accès refusé","detail":"Ce profil n'a pas accès à cette ressource."}""");
    }
}
