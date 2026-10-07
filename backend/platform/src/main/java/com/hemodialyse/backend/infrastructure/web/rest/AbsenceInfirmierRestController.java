package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.infirmier.AbsenceInfirmierService;
import com.hemodialyse.backend.domain.infirmier.model.AbsenceInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.TypeAbsence;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.TenantScope;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Absences des infirmiers (congé, maladie, formation). Lecture pour le personnel, saisie par l'administration et le
 * secrétariat. Liste paginée et bornée au centre.
 */
@RestController
@RequestMapping("/api/v1/infirmiers/absences")
public class AbsenceInfirmierRestController {

    private static final String ECRITURE = "hasAnyRole('ADMIN','SECRETAIRE')";

    private final AbsenceInfirmierService service;
    private final CenterAccessGuard centerAccessGuard;

    public AbsenceInfirmierRestController(AbsenceInfirmierService service, CenterAccessGuard centerAccessGuard) {
        this.service = service;
        this.centerAccessGuard = centerAccessGuard;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')")
    public ResponseEntity<PagedResult<AbsenceResponse>> lister(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        PagedResult<AbsenceInfirmier> paged = service.lister(centre, page, size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PagedResult.of(
                paged.items().stream().map(AbsenceResponse::de).toList(), paged.total(), paged.page(), paged.size()));
    }

    @PostMapping
    @PreAuthorize(ECRITURE)
    public ResponseEntity<AbsenceResponse> declarer(@RequestParam(required = false) UUID centerId,
                                                    @Valid @RequestBody AbsenceRequest r) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        TenantScope scope = centerAccessGuard.currentScope();
        boolean parAdministrateur = scope != null && scope.roles().contains("ROLE_ADMIN");
        AbsenceInfirmier a = service.declarer(centre, r.infirmierId(), r.debut(), r.fin(),
                TypeAbsence.valueOf(r.type()), r.motif(), parAdministrateur);
        return ResponseEntity.status(HttpStatus.CREATED).body(AbsenceResponse.de(a));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<Void> supprimer(@RequestParam(required = false) UUID centerId, @PathVariable UUID id) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        service.supprimer(centre, id);
        return ResponseEntity.noContent().build();
    }

    public record AbsenceRequest(
            @NotNull UUID infirmierId,
            @NotNull LocalDate debut,
            @NotNull LocalDate fin,
            @NotBlank String type,
            String motif) {
    }

    public record AbsenceResponse(UUID id, UUID infirmierId, LocalDate debut, LocalDate fin, TypeAbsence type,
                                  String motif) {
        static AbsenceResponse de(AbsenceInfirmier a) {
            return new AbsenceResponse(a.id(), a.infirmierId(), a.debut(), a.fin(), a.type(), a.motif());
        }
    }
}
