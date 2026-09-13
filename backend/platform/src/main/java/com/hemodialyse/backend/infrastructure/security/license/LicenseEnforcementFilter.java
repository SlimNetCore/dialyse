package com.hemodialyse.backend.infrastructure.security.license;

import com.hemodialyse.backend.application.license.LicenseService;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
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
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blocks API access for any authenticated, non-SUPERADMIN request whose center has no
 * currently valid license. Runs after {@code JwtAuthenticationFilter} so the request's
 * {@link UserPrincipal} (with its {@code centerId}) is already on the security context.
 *
 * <p>Verdicts are cached in memory per center for a short TTL — this is a
 * single-instance-per-center deployment (see licensing design), so a plain map is
 * enough; no distributed cache needed.
 */
@Component
public class LicenseEnforcementFilter extends OncePerRequestFilter {

    private static final long CACHE_TTL_SECONDS = 60;

    private static final String[] EXCLUDED_PREFIXES = {
            "/api/v1/auth/",
            "/api/v1/licenses/status",
            "/api/v1/licenses/activate",
            "/api/v1/licenses/authority/",
            "/api/v1/system/ping",
            "/actuator/",
            "/h2-console/",
            "/swagger-ui",
            "/v3/api-docs",
            // The live-notifications channel carries no business data by itself and is
            // used during app startup/initialization before license status is even
            // surfaced to the user — blocking it here would hang the frontend instead
            // of showing the proper "license blocked" screen. Actual data access still
            // goes through the REST endpoints, which remain enforced.
            "/ws"
    };

    private final LicenseService licenseService;
    private final ConcurrentHashMap<UUID, CachedVerdict> cache = new ConcurrentHashMap<>();

    public LicenseEnforcementFilter(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        for (String prefix : EXCLUDED_PREFIXES) {
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
        if (!(auth != null && auth.getPrincipal() instanceof UserPrincipal principal)) {
            // Not authenticated (yet) — let the normal security chain decide (401).
            chain.doFilter(request, response);
            return;
        }

        boolean isSuperAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPERADMIN".equals(a.getAuthority()));
        if (isSuperAdmin) {
            chain.doFilter(request, response);
            return;
        }

        String centerIdRaw = principal.getCenterId();
        if (centerIdRaw == null || centerIdRaw.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        UUID centerId = UUID.fromString(centerIdRaw);
        if (!isLicenseValid(centerId)) {
            writeBlocked(response);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isLicenseValid(UUID centerId) {
        CachedVerdict cached = cache.get(centerId);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.valid();
        }
        boolean valid = licenseService.verify(centerId).valid();
        cache.put(centerId, new CachedVerdict(valid, now.plusSeconds(CACHE_TTL_SECONDS)));
        return valid;
    }

    private void writeBlocked(HttpServletResponse response) throws IOException {
        response.setStatus(402); // Payment Required
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"code":"LICENSE_REQUIRED","title":"Licence requise","detail":"Ce centre n'a pas de licence valide. Contactez votre fournisseur."}""");
    }

    private record CachedVerdict(boolean valid, Instant expiresAt) {
    }
}
