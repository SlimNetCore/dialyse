package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.audit.AuditQueryService;
import com.hemodialyse.backend.application.audit.AuditQueryService.Entry;
import com.hemodialyse.backend.application.audit.AuditQueryService.Search;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Journal d'audit (« qui a fait quoi ») : réservé aux administrateurs — jamais à la direction, dont le principe
 * fondateur est de ne voir aucune donnée nominative (voir {@code DirectionAccessGuard}).
 * <p>
 * Un {@code ADMIN} est cantonné à son propre centre (celui de sa session, jamais un paramètre client — même
 * principe que le reste de l'application) ; le {@code SUPERADMIN} (propriétaire de la plateforme) peut filtrer sur
 * n'importe quel centre ou société, ou ne rien filtrer pour tout voir.
 */
@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
public class AuditRestController {

    private final AuditQueryService queries;

    public AuditRestController(AuditQueryService queries) {
        this.queries = queries;
    }

    private static boolean isSuperadmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).anyMatch("ROLE_SUPERADMIN"::equals);
    }

    /**
     * Le centre de la session en cours (jamais un paramètre client) : un ADMIN ne voit que son propre centre.
     */
    private static UUID requireOwnCenter() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal) || principal.getCenterId() == null) {
            throw new AccessDeniedException("Centre de rattachement introuvable");
        }
        return UUID.fromString(principal.getCenterId());
    }

    @GetMapping
    public ResponseEntity<PagedResult<Entry>> search(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) UUID societeId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String actionCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID scopedCenter = isSuperadmin() ? centerId : requireOwnCenter();
        UUID scopedSociete = isSuperadmin() ? societeId : null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(queries.search(new Search(scopedCenter, scopedSociete, userId, actionCode, from, to, page, size)));
    }

    @GetMapping("/action-codes")
    public ResponseEntity<List<String>> actionCodes(@RequestParam(required = false) UUID societeId) {
        UUID scopedCenter = isSuperadmin() ? null : requireOwnCenter();
        UUID scopedSociete = isSuperadmin() ? societeId : null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(queries.distinctActionCodes(scopedCenter, scopedSociete));
    }
}
