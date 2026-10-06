package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.application.seance.SuppressionSeanceService;
import com.hemodialyse.backend.domain.seance.model.SuppressionSeance;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Suppression d'une séance (administrateur) : motif obligatoire, refusée pour une séance facturée ; le stock consommé
 * est restitué et la suppression journalisée. Bornée au centre de la session ({@link CenterAccessGuard}).
 */
@RestController
@RequestMapping("/api/v1/seances")
@PreAuthorize("hasRole('ADMIN')")
public class SeanceSuppressionRestController {

    private final SuppressionSeanceService suppression;
    private final NotificationService notifications;
    private final CenterAccessGuard centerAccessGuard;

    public SeanceSuppressionRestController(SuppressionSeanceService suppression, NotificationService notifications,
                                           CenterAccessGuard centerAccessGuard) {
        this.suppression = suppression;
        this.notifications = notifications;
        this.centerAccessGuard = centerAccessGuard;
    }

    @DeleteMapping("/{seanceId}")
    public ResponseEntity<Void> supprimer(@RequestParam(required = false) UUID centerId, @PathVariable UUID seanceId,
                                          @RequestBody SuppressionRequest request) {
        CenterId centre = centerAccessGuard.requireCenter(centerId);
        SuppressionSeance faite = suppression.supprimer(centre, seanceId, request == null ? null : request.motif(),
                CurrentUser.username());
        notifications.notifySeanceSupprimee(centre.value(), seanceId, faite.patientId(), faite.dateSeance());
        return ResponseEntity.noContent().build();
    }

    public record SuppressionRequest(String motif) {
    }
}
