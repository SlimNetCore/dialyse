package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.port.DossierMedicalPatientUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertDossierMedicalPatientRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.DossierMedicalPatientResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.EntityWriteResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Dossier médical de base du patient : néphropathie initiale, mise en dialyse, statuts sérologiques
 * hépatite B/C et observation globale.
 * <p>
 * Accès réservé au corps médical : le MEDECIN écrit, l'ADMIN consulte (AGENTS.md §2 pour le centre).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class DossierMedicalPatientRestController {

    private final DossierMedicalPatientUseCase useCase;
    private final CenterAccessGuard centerAccessGuard;

    public DossierMedicalPatientRestController(DossierMedicalPatientUseCase useCase,
                                               CenterAccessGuard centerAccessGuard) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
    }

    /**
     * Lecture ouverte à l'infirmier : le statut sérologique hépatite B/C conditionne l'isolation
     * machine. Le lui masquer serait un risque de soin. L'écriture reste réservée au médecin.
     */
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN','INFIRMIER')")
    @GetMapping("/{patientId}/dossier-medical")
    public ResponseEntity<DossierMedicalPatientResponse> get(@PathVariable UUID patientId,
                                                             @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        return useCase.getByPatient(center, patientId)
                .map(d -> ResponseEntity.ok(DossierMedicalPatientResponse.from(d)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/dossier-medical")
    public ResponseEntity<EntityWriteResponse> create(@PathVariable UUID patientId,
                                                      @RequestBody @Valid UpsertDossierMedicalPatientRequest request) {
        return ResponseEntity.ok(upsert(patientId, request));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/dossier-medical")
    public ResponseEntity<EntityWriteResponse> update(@PathVariable UUID patientId,
                                                      @RequestBody @Valid UpsertDossierMedicalPatientRequest request) {
        return ResponseEntity.ok(upsert(patientId, request));
    }

    private EntityWriteResponse upsert(UUID patientId, UpsertDossierMedicalPatientRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        var dossier = useCase.upsert(
                center,
                patientId,
                request.nephropathieInitiale(),
                request.dateMiseEnDialyse(),
                request.hepatiteBStatut(),
                request.hepatiteCStatut(),
                request.observationGlobale()
        );
        return new EntityWriteResponse(dossier.getId(), dossier.getPatientId(), dossier.getUpdatedAt());
    }
}
