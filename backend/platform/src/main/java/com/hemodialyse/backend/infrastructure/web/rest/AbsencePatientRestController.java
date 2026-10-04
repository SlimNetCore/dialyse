package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.absence.AbsencePatientService;
import com.hemodialyse.backend.application.notification.SaisieInfirmierNotifier;
import com.hemodialyse.backend.domain.absence.model.AbsenceFiltre;
import com.hemodialyse.backend.domain.absence.model.AbsenceLigne;
import com.hemodialyse.backend.domain.absence.model.AbsencePatient;
import com.hemodialyse.backend.domain.absence.model.MotifAbsence;
import com.hemodialyse.backend.domain.absence.model.StatutAbsence;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
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
import java.util.List;
import java.util.UUID;

/**
 * Suivi des absences de patients du centre courant : liste paginée filtrable, déclaration, qualification (motif),
 * rattrapage et annulation. La correction d'une absence déjà qualifiée est réservée à l'administrateur et au médecin.
 */
@RestController
@RequestMapping("/api/v1/absences-patients")
public class AbsencePatientRestController {

    private static final String ACCES = "hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')";

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

    @GetMapping
    @PreAuthorize(ACCES)
    public ResponseEntity<PagedResult<AbsenceResponse>> lister(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) StatutAbsence statut,
            @RequestParam(required = false) MotifAbsence motif,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        PagedResult<AbsenceLigne> paged = service.lister(centre, new AbsenceFiltre(statut, motif, patientId, from, to),
                page, size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PagedResult.of(
                paged.items().stream().map(l -> AbsenceResponse.de(l.absence(), l.patientNom())).toList(),
                paged.total(), paged.page(), paged.size()));
    }

    /**
     * Suivi de la semaine du planning (grille bornée à 7 jours, donc non paginée) : absences et séances réalisées, qui
     * colorent les séances du planning.
     */
    @GetMapping("/semaine")
    @PreAuthorize(ACCES)
    public ResponseEntity<AbsencePatientService.SuiviSemaine> semaine(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        LocalDate jour = date != null ? date : LocalDate.now(ZoneOffset.UTC);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.semaine(centre, jour));
    }

    @GetMapping("/synthese")
    @PreAuthorize(ACCES)
    public ResponseEntity<SyntheseResponse> synthese(@RequestParam(required = false) UUID centerId) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        AbsencePatientService.Synthese s = service.synthese(centre);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SyntheseResponse(s.aQualifier(), s.enRetard()));
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

    @PostMapping
    @PreAuthorize(ACCES)
    public ResponseEntity<AbsenceResponse> declarer(@RequestParam(required = false) UUID centerId,
                                                    @Valid @RequestBody DeclarationRequest r,
                                                    Authentication authentication) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        AbsencePatient a = service.declarer(centre, r.patientId(), r.dateSeance(),
                r.motif() == null ? null : MotifAbsence.valueOf(r.motif()), r.commentaire(),
                utilisateur(authentication));
        saisieNotifier.saisie(centre, "ABSENCE", a.patientId(), a.dateSeance());
        return ResponseEntity.status(HttpStatus.CREATED).body(AbsenceResponse.de(a, null));
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
        return ResponseEntity.ok(AbsenceResponse.de(a, null));
    }

    @PutMapping("/{id}/rattrapage")
    @PreAuthorize(ACCES)
    public ResponseEntity<AbsenceResponse> rattraper(@RequestParam(required = false) UUID centerId,
                                                     @PathVariable UUID id, @Valid @RequestBody RattrapageRequest r,
                                                     Authentication authentication) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        AbsencePatient a = service.rattraper(centre, id, r.dateRattrapage(), utilisateur(authentication));
        saisieNotifier.saisie(centre, "ABSENCE", a.patientId(), a.dateSeance());
        return ResponseEntity.ok(AbsenceResponse.de(a, null));
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
        return ResponseEntity.ok(AbsenceResponse.de(a, null));
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

    public record SyntheseResponse(long aQualifier, long enRetard) {
    }

    public record PeriodeRequest(@NotNull LocalDate from, @NotNull LocalDate to) {
    }

    public record RattrapageDetectionResponse(int creees, int annulees) {
    }

    public record AbsenceResponse(UUID id, UUID patientId, String patientNom, LocalDate dateSeance, String source,
                                  String statut, String motif, String commentaire, String forfaitLibelle,
                                  BigDecimal prixTtc, BigDecimal tauxTva, BigDecimal montantHt, Instant declareeLe,
                                  Instant qualifieeLe, LocalDate dateRattrapage, boolean enRetard) {
        static AbsenceResponse de(AbsencePatient a, String patientNom) {
            return new AbsenceResponse(a.id(), a.patientId(), patientNom, a.dateSeance(), a.source().name(),
                    a.statut().name(), a.motif() == null ? null : a.motif().name(), a.commentaire(),
                    a.valeur().forfaitLibelle(), a.valeur().prixTtc(), a.valeur().tauxTva(), a.valeur().montantHt(),
                    a.declareeLe(), a.qualifieeLe(), a.dateRattrapage(),
                    a.qualificationEnRetard(LocalDate.now(ZoneOffset.UTC)));
        }
    }
}
