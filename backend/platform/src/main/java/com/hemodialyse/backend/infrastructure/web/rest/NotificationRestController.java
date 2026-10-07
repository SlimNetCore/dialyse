package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.notification.NotificationJournalPort;
import com.hemodialyse.backend.application.notification.NotificationJournalPort.Alerte;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.TenantScope;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Alertes durables du centre de l'utilisateur : celles qui lui sont destinées (selon ses rôles), lues ou non, pour les
 * retrouver à la connexion. Liste paginée, bornée au centre de la session.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationRestController {

    private static final int TAILLE_MAX = 100;

    private final NotificationJournalPort journal;
    private final CenterAccessGuard centerAccessGuard;

    public NotificationRestController(NotificationJournalPort journal, CenterAccessGuard centerAccessGuard) {
        this.journal = journal;
        this.centerAccessGuard = centerAccessGuard;
    }

    private static Set<String> roles(TenantScope scope) {
        return scope.roles().stream().map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
                .collect(Collectors.toUnmodifiableSet());
    }

    @GetMapping
    public ResponseEntity<PagedResult<AlerteResponse>> lister(@RequestParam(required = false) UUID centerId,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "50") int size) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        TenantScope scope = centerAccessGuard.currentScope();
        PagedResult<Alerte> alertes = journal.lister(centre, scope.userId(), roles(scope), Math.max(page, 0),
                Math.min(Math.max(size, 1), TAILLE_MAX));
        List<AlerteResponse> items = alertes.items().stream().map(a -> new AlerteResponse(a.id(), a.type(), centre,
                a.payload(), a.creeLe().toString(), a.lue())).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(PagedResult.of(items, alertes.total(), alertes.page(), alertes.size()));
    }

    @PostMapping("/lecture")
    public ResponseEntity<Void> marquerLues(@RequestParam(required = false) UUID centerId,
                                            @RequestBody LecturesRequest request) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        journal.marquerLues(centre, centerAccessGuard.currentScope().userId(), request.ids());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/lecture-tout")
    public ResponseEntity<Void> toutMarquerLu(@RequestParam(required = false) UUID centerId) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        TenantScope scope = centerAccessGuard.currentScope();
        journal.toutMarquerLu(centre, scope.userId(), roles(scope));
        return ResponseEntity.noContent().build();
    }

    /**
     * Même forme qu'un évènement temps réel ({@code type}, {@code payload}, {@code timestamp}), avec l'identifiant et
     * l'état de lecture.
     */
    public record AlerteResponse(UUID id, String type, UUID centerId, Map<String, String> payload, String timestamp,
                                 boolean lue) {
    }

    public record LecturesRequest(@NotNull List<UUID> ids) {
    }
}
