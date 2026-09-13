package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.medical.anemie.port.AdministrationTraitementUseCase;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.DoseAdministree;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateAdministrationTraitementRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.AdministrationTraitementResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.PagedResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Administrations réelles du traitement de l'anémie (EPO, fer injectable) — distinctes de la
 * prescription : ce qui a effectivement été donné au patient, pas seulement ce qui était prévu.
 * <p>
 * Accès réservé au corps médical : le MEDECIN écrit, l'ADMIN consulte.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class AdministrationTraitementRestController {

    private final AdministrationTraitementUseCase useCase;
    private final CenterAccessGuard centerAccessGuard;

    public AdministrationTraitementRestController(AdministrationTraitementUseCase useCase,
                                                  CenterAccessGuard centerAccessGuard) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/administrations-anemie")
    public ResponseEntity<PagedResponse<AdministrationTraitementResponse>> list(
            @PathVariable UUID patientId,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        var paged = useCase.listPagedByPatient(center, patientId, page, size);
        return ResponseEntity.ok(PagedResponse.from(paged, AdministrationTraitementResponse::from));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/administrations-anemie")
    public ResponseEntity<AdministrationTraitementResponse> create(
            @PathVariable UUID patientId, @RequestBody @Valid CreateAdministrationTraitementRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        DoseAdministree dose = request.dose() == null ? null : new DoseAdministree(request.dose(), request.uniteDose());
        var administration = useCase.create(center, patientId, request.prescriptionMedicaleId(),
                TypeTraitementAnemie.valueOf(request.typeTraitement()), request.molecule(), dose, request.voie(),
                request.dateAdministration(), request.seanceId(), request.administrePar(), request.administree(),
                request.motifNonAdministration());
        return ResponseEntity.ok(AdministrationTraitementResponse.from(administration));
    }
}
