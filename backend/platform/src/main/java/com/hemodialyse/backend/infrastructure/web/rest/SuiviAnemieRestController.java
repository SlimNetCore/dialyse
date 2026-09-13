package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.query.SuiviAnemieQueryService;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.response.SuiviAnemieResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Vue agrégée du suivi de l'anémie (Hb, ferritine, CST, albumine, évaluation KDIGO, prescription
 * EPO/fer active, administrations réelles). Lecture seule : la saisie passe par les endpoints
 * dédiés (résultats d'analyses, prescriptions, administrations d'anémie).
 * <p>
 * Accès réservé au corps médical (MEDECIN et ADMIN, en lecture uniquement).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class SuiviAnemieRestController {

    private final SuiviAnemieQueryService queryService;
    private final CenterAccessGuard centerAccessGuard;

    public SuiviAnemieRestController(SuiviAnemieQueryService queryService, CenterAccessGuard centerAccessGuard) {
        this.queryService = queryService;
        this.centerAccessGuard = centerAccessGuard;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/suivi-anemie")
    public ResponseEntity<SuiviAnemieResponse> get(@PathVariable UUID patientId,
                                                   @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        return ResponseEntity.ok(queryService.getSuiviAnemie(center.value(), patientId));
    }
}
