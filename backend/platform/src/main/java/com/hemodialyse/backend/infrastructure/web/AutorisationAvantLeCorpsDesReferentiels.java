package com.hemodialyse.backend.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * PL11-2 : l'autorisation de l'administration des référentiels passe avant la lecture du corps. Sans cela, un rôle non autorisé qui
 * envoie un corps sans type de contenu reçoit 415 (la résolution du corps précède la garde de méthode) au lieu de 403. Seul un rôle
 * autorisé reçoit 415 pour un type de contenu illisible. Même refus qu'à la garde de méthode ({@code hasRole('ADMIN')}).
 */
@Configuration
public class AutorisationAvantLeCorpsDesReferentiels implements WebMvcConfigurer {

    private static final String RACINE = "/api/v1/admin/referentials";

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws HttpMediaTypeNotSupportedException {
                if (!"POST".equals(request.getMethod()) && !"PUT".equals(request.getMethod())) return true;
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                boolean admin = authentication != null && authentication.isAuthenticated()
                        && authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
                // Rôle non autorisé : rien ici, le refus 403 est produit par le chemin normal (garde de méthode ou conseil d'erreurs).
                if (admin && !request.getRequestURI().endsWith("/import")) exigerUnCorpsJson(request);
                return true;
            }
        }).addPathPatterns(RACINE + "/**");
    }

    /** Le type de contenu est contrôlé après l'autorisation (ancien {@code consumes}) : JSON seul, sinon 415. */
    private static void exigerUnCorpsJson(HttpServletRequest request) throws HttpMediaTypeNotSupportedException {
        String entete = request.getContentType();
        MediaType json = MediaType.APPLICATION_JSON;
        try {
            if (entete != null && json.isCompatibleWith(MediaType.parseMediaType(entete))) return;
        } catch (InvalidMediaTypeException illisible) {
            // traité comme un type non supporté
        }
        throw entete == null ? new HttpMediaTypeNotSupportedException("Content-Type absent", java.util.List.of(json))
                : new HttpMediaTypeNotSupportedException(entete + " non supporté", java.util.List.of(json));
    }
}
