package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.absence.AbsencePatientService;
import com.hemodialyse.backend.application.notification.SaisieInfirmierNotifier;
import com.hemodialyse.backend.domain.absence.model.AbsencePatient;
import com.hemodialyse.backend.domain.absence.model.MotifAbsence;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Suivi des absences de patients du centre courant : déclaration, qualification (motif), rattrapage et annulation (la
 * consultation est passée au contexte absences-patients, slice A1). La correction d'une absence déjà qualifiée est réservée à l'administrateur et au médecin.
 */
@RestController
@RequestMapping("/api/v1/absences-patients")
public class AbsencePatientRestController {

    private static final String ACCES = "hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')";
    private static final String DECLARATION = "hasAnyRole('ADMIN','SECRETAIRE','INFIRMIER')";

    private final AbsencePatientService service;
    private final CenterAccessGuard centerAccessGuard;
    private final SaisieInfirmierNotifier saisieNotifier;

    public AbsencePatientRestController(AbsencePatientService service, CenterAccessGuard centerAccessGuard,
                                        SaisieInfirmierNotifier saisieNotifier) {
        this.service = service;
        this.centerAccessGuard = centerAccessGuard;
        this.saisieNotifier = saisieNotifier;
    }

    private static UUID utilisateur(Authentication authentication) {
        return UUID.fromString(((UserPrincipal) authentication.getPrincipal()).getId());
    }

    private static boolean peutCorriger(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MEDECIN"));
    }

    // Consultation (liste, semaine, synthèse, séances de rattrapage) : contexte absences-patients, slice A1
    // (backend/contexts/absences-patients, AbsencesControleur) sur les mêmes URLs.

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

    /**
     * Déclaration d'une absence : administrateur, secrétariat et infirmier. Le médecin consulte le planning et le suivi
     * des absences, mais ne déclare pas (il peut en revanche corriger une absence déjà qualifiée).
     */
    @PostMapping
    @PreAuthorize(DECLARATION)
    public ResponseEntity<AbsenceResponse> declarer(@RequestParam(required = false) UUID centerId,
                                                    @Valid @RequestBody DeclarationRequest r,
                                                    Authentication authentication) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        AbsencePatient a = service.declarer(centre, r.patientId(), r.dateSeance(),
                r.motif() == null ? null : MotifAbsence.valueOf(r.motif()), r.commentaire(),
                utilisateur(authentication));
        saisieNotifier.saisie(centre, "ABSENCE", a.patientId(), a.dateSeance());
        return ResponseEntity.status(HttpStatus.CREATED).body(AbsenceResponse.de(a));
    }

    @PutMapping("/{id}/qualification")
    @PreAuthorize(ACCES)
    public ResponseEntity<AbsenceResponse> qualifier(@RequestParam(required = false) UUID centerId,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody QualificationRequest r,
                                                     Authentication authentication) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        AbsencePatient a = service.qualifier(centre, id, MotifAbsence.valueOf(r.motif()), r.commentaire(),
                utilisateur(authentication), peutCorriger(authentication));
        saisieNotifier.saisie(centre, "ABSENCE", a.patientId(), a.dateSeance());
        return ResponseEntity.ok(AbsenceResponse.de(a));
    }

    @PutMapping("/{id}/rattrapage")
    @PreAuthorize(ACCES)
    public ResponseEntity<AbsenceResponse> rattraper(@RequestParam(required = false) UUID centerId,
                                                     @PathVariable UUID id, @Valid @RequestBody RattrapageRequest r,
                                                     Authentication authentication) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        AbsencePatient a = service.rattraper(centre, id, r.dateRattrapage(), utilisateur(authentication));
        saisieNotifier.saisie(centre, "ABSENCE", a.patientId(), a.dateSeance());
        return ResponseEntity.ok(AbsenceResponse.de(a));
    }

    @PutMapping("/{id}/annulation")
    @PreAuthorize(ACCES)
    public ResponseEntity<AbsenceResponse> annuler(@RequestParam(required = false) UUID centerId,
                                                   @PathVariable UUID id, @Valid @RequestBody AnnulationRequest r,
                                                   Authentication authentication) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        AbsencePatient a = service.annuler(centre, id, r.commentaire(), utilisateur(authentication),
                peutCorriger(authentication));
        saisieNotifier.saisie(centre, "ABSENCE", a.patientId(), a.dateSeance());
        return ResponseEntity.ok(AbsenceResponse.de(a));
    }

    public record DeclarationRequest(@NotNull UUID patientId, @NotNull LocalDate dateSeance, String motif,
                                     String commentaire) {
    }

    public record QualificationRequest(@NotBlank String motif, String commentaire) {
    }

    public record RattrapageRequest(@NotNull LocalDate dateRattrapage) {
    }

    public record AnnulationRequest(@NotBlank String commentaire) {
    }

    public record PeriodeRequest(@NotNull LocalDate from, @NotNull LocalDate to) {
    }

    public record RattrapageDetectionResponse(int creees, int annulees) {
    }

    public record AbsenceResponse(UUID id, UUID patientId, String patientNom, LocalDate dateSeance, String source,
                                  String statut, String motif, String commentaire, String forfaitLibelle,
                                  BigDecimal prixTtc, BigDecimal tauxTva, BigDecimal montantHt, Instant declareeLe,
                                  Instant qualifieeLe, LocalDate dateRattrapage, boolean enRetard) {
        static AbsenceResponse de(AbsencePatient a) {
            return new AbsenceResponse(a.id(), a.patientId(), null, a.dateSeance(), a.source().name(),
                    a.statut().name(), a.motif() == null ? null : a.motif().name(), a.commentaire(),
                    a.valeur().forfaitLibelle(), a.valeur().prixTtc(), a.valeur().tauxTva(), a.valeur().montantHt(),
                    a.declareeLe(), a.qualifieeLe(), a.dateRattrapage(),
                    a.qualificationEnRetard(LocalDate.now(ZoneOffset.UTC)));
        }
    }
}
