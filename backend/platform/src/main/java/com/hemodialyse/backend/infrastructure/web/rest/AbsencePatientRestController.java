package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.absence.AbsencePatientService;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Détection d'une période passée des absences de patients (slice A3 à venir). La consultation est passée au contexte
 * absences-patients (slice A1) et le cycle de vie (déclaration, qualification, rattrapage, annulation) à la slice A2
 * (backend/contexts/absences-patients), sur les mêmes URLs.
 */
@RestController
@RequestMapping("/api/v1/absences-patients")
public class AbsencePatientRestController {

    private final AbsencePatientService service;
    private final CenterAccessGuard centerAccessGuard;

    public AbsencePatientRestController(AbsencePatientService service, CenterAccessGuard centerAccessGuard) {
        this.service = service;
        this.centerAccessGuard = centerAccessGuard;
    }

    /**
     * Rattrapage d'une période passée (absences jamais détectées) : réservé à l'administrateur du centre.
     */
    @PostMapping("/rattrapage-detection")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RattrapageDetectionResponse> rattraperDetection(
            @RequestParam(required = false) UUID centerId, @Valid @RequestBody PeriodeRequest r) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        AbsencePatientService.Rattrapage res = service.rattraperPeriode(centre, r.from(), r.to());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new RattrapageDetectionResponse(res.creees(), res.annulees()));
    }

    public record PeriodeRequest(@NotNull LocalDate from, @NotNull LocalDate to) {
    }

    public record RattrapageDetectionResponse(int creees, int annulees) {
    }
}
