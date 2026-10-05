package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.infirmier.MonPlanningInfirmierService;
import com.hemodialyse.backend.application.infirmier.MonPlanningInfirmierService.CaseMonPlanning;
import com.hemodialyse.backend.application.infirmier.MonPlanningInfirmierService.MonPlanning;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.JourPlanning;
import com.hemodialyse.backend.domain.infirmier.model.AbsenceInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.CreneauPersonnel;
import com.hemodialyse.backend.domain.infirmier.model.TypeAbsence;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.rest.AbsenceInfirmierRestController.AbsenceResponse;
import com.hemodialyse.backend.infrastructure.web.rest.InfirmierRestController.InfirmierResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * « Mon planning » : planning personnel et absences de l'infirmier dont la fiche est reliée au compte connecté. Le
 * compte vient toujours de l'authentification (jamais d'un paramètre) : on ne peut consulter ni modifier que soi-même.
 */
@RestController
@RequestMapping("/api/v1/infirmiers/moi")
@PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')")
public class MonPlanningInfirmierRestController {

    private final MonPlanningInfirmierService service;
    private final CenterAccessGuard centerAccessGuard;

    public MonPlanningInfirmierRestController(MonPlanningInfirmierService service, CenterAccessGuard centerAccessGuard) {
        this.service = service;
        this.centerAccessGuard = centerAccessGuard;
    }

    private static UUID utilisateur(Authentication authentication) {
        return UUID.fromString(((UserPrincipal) authentication.getPrincipal()).getId());
    }

    @GetMapping("/planning")
    public ResponseEntity<MonPlanningResponse> planning(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        LocalDate jour = date != null ? date : LocalDate.now(ZoneOffset.UTC);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(MonPlanningResponse.de(service.planning(centre, utilisateur(authentication), jour)));
    }

    @GetMapping("/absences")
    public ResponseEntity<PagedResult<AbsenceResponse>> absences(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        PagedResult<AbsenceInfirmier> paged = service.mesAbsences(centre, utilisateur(authentication), page, size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PagedResult.of(
                paged.items().stream().map(AbsenceResponse::de).toList(), paged.total(), paged.page(), paged.size()));
    }

    @PostMapping("/absences")
    public ResponseEntity<AbsenceResponse> declarer(@RequestParam(required = false) UUID centerId,
                                                    @Valid @RequestBody MonAbsenceRequest r,
                                                    Authentication authentication) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        AbsenceInfirmier absence = service.declarer(centre, utilisateur(authentication), LocalDate.now(ZoneOffset.UTC),
                r.debut(), r.fin(), TypeAbsence.valueOf(r.type()), r.motif());
        return ResponseEntity.status(HttpStatus.CREATED).body(AbsenceResponse.de(absence));
    }

    @DeleteMapping("/absences/{id}")
    public ResponseEntity<Void> annuler(@RequestParam(required = false) UUID centerId, @PathVariable UUID id,
                                        Authentication authentication) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        service.annuler(centre, utilisateur(authentication), LocalDate.now(ZoneOffset.UTC), id);
        return ResponseEntity.noContent().build();
    }

    public record MonAbsenceRequest(
            @NotNull LocalDate debut,
            @NotNull LocalDate fin,
            @NotBlank String type,
            String motif) {
    }

    public record MonPlanningResponse(InfirmierResponse infirmier, LocalDate debut, LocalDate fin,
                                      List<SalleRef> salles,
                                      List<CreneauRef> creneaux, List<CreneauPersonnel> mesCreneaux,
                                      List<JourPlanning> jours, List<CaseMonPlanning> mesCases) {
        static MonPlanningResponse de(MonPlanning p) {
            return new MonPlanningResponse(InfirmierResponse.de(p.infirmier()), p.debut(), p.fin(), p.salles(),
                    p.creneaux(), p.mesCreneaux(), p.jours(), p.mesCases());
        }
    }
}
