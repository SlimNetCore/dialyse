package com.hemodialyse.backend.infrastructure.security;

import com.hemodialyse.backend.application.audit.AuditActionResolver;
import com.hemodialyse.backend.application.audit.AuditEvent;
import com.hemodialyse.backend.application.audit.AuditWriterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Traçabilité « qui a fait quoi » (module de traçabilité) : trace automatiquement toute écriture
 * ({@code POST}/{@code PUT}/{@code PATCH}/{@code DELETE}) sur {@code /api/v1/**}, plus quelques lectures
 * sensibles explicitement désignées (voir {@link AuditActionResolver}).
 * <p>
 * S'exécute après la chaîne Spring Security (bean {@link OncePerRequestFilter} sans ordre explicite : Spring Boot
 * l'enregistre après le filtre de sécurité, qui a donc déjà authentifié la requête — même principe que
 * {@link RoleScopeFilter}), et après le {@code DispatcherServlet} : le gabarit de route
 * ({@code /api/v1/patients/{id}}, jamais l'URL brute) est déjà résolu et disponible en attribut de requête, ce qui
 * évite l'explosion de cardinalité et les identifiants nominatifs dans le libellé.
 * <p>
 * N'écrit jamais en base sur le thread de la requête : l'évènement est mis en file par
 * {@link AuditWriterService#enqueue}, écrit par lot en arrière-plan. Un échec de traçabilité n'affecte jamais la
 * réponse envoyée à l'appelant.
 */
@Component
public class AuditRequestFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuditRequestFilter.class);
    private static final String API_PREFIX = "/api/v1/";

    private final AuditWriterService writer;

    public AuditRequestFilter(AuditWriterService writer) {
        this.writer = writer;
    }

    private static String routeTemplate(HttpServletRequest request) {
        Object attr = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return attr instanceof String s ? s : null;
    }

    @SuppressWarnings("unchecked")
    private static String entityId(HttpServletRequest request) {
        Object attr = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attr instanceof Map<?, ?> vars) || vars.isEmpty()) return null;
        // La dernière variable de chemin est en général l'identifiant le plus spécifique (ex. {patientId} avant
        // {id} d'une sous-ressource) ; à défaut de mieux sans coupler ce filtre à chaque contrôleur.
        Object last = null;
        for (Object v : ((Map<String, Object>) vars).values()) {
            last = v;
        }
        return last == null ? null : String.valueOf(last);
    }

    private static String roles(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(API_PREFIX) || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            try {
                record(request, response, System.currentTimeMillis() - start);
            } catch (RuntimeException e) {
                // La traçabilité ne doit jamais faire échouer une requête ni masquer une exception métier.
                log.warn("Évènement d'audit non journalisé pour {} {}", request.getMethod(), request.getRequestURI(), e);
            }
        }
    }

    private void record(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        String method = request.getMethod();
        String routeTemplate = routeTemplate(request);
        if (!AuditActionResolver.isTracked(method, routeTemplate)) {
            return;
        }
        AuditActionResolver.Resolved resolved = AuditActionResolver.resolve(method, routeTemplate);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = auth != null && auth.getPrincipal() instanceof UserPrincipal p ? p : null;

        writer.enqueue(new AuditEvent(
                OffsetDateTime.now(ZoneOffset.UTC),
                parseUuid(principal == null ? null : principal.getId()),
                principal == null ? null : principal.getUsername(),
                principal == null ? null : roles(auth),
                parseUuid(principal == null ? null : principal.getCenterId()),
                parseUuid(principal == null ? null : principal.getSocieteId()),
                resolved.actionCode(),
                resolved.entityType(),
                entityId(request),
                resolved.libelle(),
                method,
                routeTemplate,
                response.getStatus(),
                durationMs,
                request.getRemoteAddr()
        ));
    }
}
