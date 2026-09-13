package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.license.LicenseService;
import com.hemodialyse.backend.infrastructure.persistence.entity.CenterJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.LicenseJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.CenterJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.repository.LicenseJpaRepository;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.security.license.LicenseKeyProperties;
import com.hemodialyse.backend.infrastructure.web.dto.request.ActivateLicenseRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.IssueLicenseRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.RevokeLicenseRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * License management — the whole controller is restricted to the SUPERADMIN role
 * (the vendor's own account) except {@code /status} (any authenticated user, to
 * power the frontend's license banner) and {@code /authority/verify} (public,
 * server-to-server, answered only by the vendor's own "authority" instance).
 */
@RestController
@RequestMapping("/api/v1/licenses")
public class LicenseRestController {

    private final LicenseService licenseService;
    private final LicenseJpaRepository licenseRepository;
    private final CenterJpaRepository centerRepository;
    private final LicenseKeyProperties keyProperties;

    public LicenseRestController(LicenseService licenseService,
                                 LicenseJpaRepository licenseRepository,
                                 CenterJpaRepository centerRepository,
                                 LicenseKeyProperties keyProperties) {
        this.licenseService = licenseService;
        this.licenseRepository = licenseRepository;
        this.centerRepository = centerRepository;
        this.keyProperties = keyProperties;
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/issue")
    public ResponseEntity<?> issue(@RequestBody IssueLicenseRequest req, Authentication authentication) {
        UUID createdBy = UUID.fromString(((UserPrincipal) authentication.getPrincipal()).getId());
        LicenseJpaEntity entity = licenseService.issue(
                req.centerId(), req.type(), req.maxUsers(), req.validFrom(), req.validUntil(), createdBy);
        return ResponseEntity.ok(toPayload(entity));
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @GetMapping
    public ResponseEntity<?> listAll() {
        Map<UUID, String> centerNames = new LinkedHashMap<>();
        for (CenterJpaEntity center : centerRepository.findAll()) {
            centerNames.put(center.getId(), center.getName());
        }
        List<Map<String, Object>> items = licenseService.listAll().stream()
                .map(l -> toPayload(l, centerNames.get(l.getCenterId())))
                .toList();
        return ResponseEntity.ok(items);
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/{id}/revoke")
    public ResponseEntity<?> revoke(@PathVariable UUID id, @RequestBody RevokeLicenseRequest req) {
        licenseService.revoke(id, req.reason());
        return ResponseEntity.ok(Map.of("revoked", true));
    }

    /**
     * Activates a license key generated on the authority instance. A center's own
     * ADMIN can only activate for their own center (the body's centerId is ignored
     * and overridden with the caller's own center, unless the caller is SUPERADMIN).
     * Restricted to ADMIN/SUPERADMIN — a regular staff account (nurse, secretary...)
     * must not be able to change the center's license.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    @PostMapping("/activate")
    public ResponseEntity<?> activate(@RequestBody ActivateLicenseRequest req, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        boolean isSuperAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPERADMIN".equals(a.getAuthority()));
        UUID centerId = isSuperAdmin ? req.centerId() : UUID.fromString(principal.getCenterId());
        UUID activatedBy = UUID.fromString(principal.getId());
        LicenseJpaEntity entity = licenseService.activate(centerId, req.licenseKey(), activatedBy);
        return ResponseEntity.ok(toPayload(entity));
    }

    /**
     * Powers the frontend's license status banner for the caller's own center.
     */
    @GetMapping("/status")
    public ResponseEntity<?> status(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        boolean isSuperAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPERADMIN".equals(a.getAuthority()));
        if (isSuperAdmin || principal.getCenterId() == null) {
            return ResponseEntity.ok(Map.of("applicable", false));
        }
        UUID centerId = UUID.fromString(principal.getCenterId());
        LicenseService.LicenseVerdict verdict = licenseService.verify(centerId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applicable", true);
        body.put("valid", verdict.valid());
        body.put("reason", verdict.reason());
        if (verdict.claims() != null) {
            body.put("type", verdict.claims().type());
            body.put("maxUsers", verdict.claims().maxUsers());
            body.put("expiresAt", verdict.claims().expiresAt());
            body.put("usedSeats", licenseService.countActiveUsers(centerId));
        }
        return ResponseEntity.ok(body);
    }

    /**
     * Server-to-server check used by client instances' periodic online verification
     * job. Only ever answers meaningfully on the vendor's own authority instance —
     * every other (client) instance returns 404, since its local `license` table is
     * just a cache, not the authoritative registry.
     */
    @GetMapping("/authority/verify")
    public ResponseEntity<?> authorityVerify(@RequestParam String jti) {
        if (!keyProperties.isAuthority()) {
            return ResponseEntity.notFound().build();
        }
        return licenseRepository.findByJti(jti)
                .map(l -> ResponseEntity.ok(Map.of(
                        "revoked", LicenseService.STATUS_REVOKED.equals(l.getStatus()))))
                .orElseGet(() -> ResponseEntity.ok(Map.of("revoked", false, "unknown", true)));
    }

    private Map<String, Object> toPayload(LicenseJpaEntity entity) {
        return toPayload(entity, null);
    }

    private Map<String, Object> toPayload(LicenseJpaEntity entity, String centerName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", entity.getId());
        payload.put("centerId", entity.getCenterId());
        payload.put("centerName", centerName);
        // Only ever returned from /issue, /activate and the SUPERADMIN-only list — the
        // caller either just generated this key (vendor) or just pasted it themselves
        // (center admin), so echoing it back here is not a new exposure.
        payload.put("licenseKey", entity.getLicenseKey());
        payload.put("type", entity.getType());
        payload.put("maxUsers", entity.getMaxUsers());
        payload.put("validFrom", entity.getValidFrom());
        payload.put("validUntil", entity.getValidUntil());
        payload.put("status", entity.getStatus());
        payload.put("activatedAt", entity.getActivatedAt());
        payload.put("lastOnlineCheckAt", entity.getLastOnlineCheckAt());
        payload.put("revokedReason", entity.getRevokedReason());
        payload.put("createdAt", entity.getCreatedAt());
        return payload;
    }
}
