package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.medical.observation.aggregate.ObservationBiologique;
import com.hemodialyse.backend.domain.medical.observation.port.ObservationBiologiqueUseCase;
import com.hemodialyse.backend.domain.medical.observation.valueobject.StatutObservation;
import com.hemodialyse.backend.domain.medical.observation.valueobject.ValeurMesuree;
import com.hemodialyse.backend.domain.medical.shared.valueobject.CodingSystem;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.request.CorrigerObservationRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateObservationRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.ObservationBiologiqueResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.PagedResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Observations biologiques du patient, codées LOINC — modèle générique qui coexiste avec le
 * bilan à colonnes fixes {@code ResultatAnalyse} (voir le plan de la Phase 4 pour leur unification).
 * <p>
 * Accès réservé au corps médical : le MEDECIN écrit, l'ADMIN consulte.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class ObservationBiologiqueRestController {

    private final ObservationBiologiqueUseCase useCase;
    private final CenterAccessGuard centerAccessGuard;

    public ObservationBiologiqueRestController(ObservationBiologiqueUseCase useCase,
                                               CenterAccessGuard centerAccessGuard) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/observations")
    public ResponseEntity<PagedResponse<ObservationBiologiqueResponse>> list(
            @PathVariable UUID patientId,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        var paged = useCase.listPagedByPatient(center, patientId, code, from, to, page, size);
        return ResponseEntity.ok(PagedResponse.from(paged, ObservationBiologiqueResponse::from));
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/demandes-examen/{demandeId}/observations")
    public ResponseEntity<List<ObservationBiologiqueResponse>> listByDemande(
            @PathVariable UUID patientId, @PathVariable UUID demandeId,
            @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        return ResponseEntity.ok(useCase.listByDemandeExamen(center, demandeId).stream()
                .map(ObservationBiologiqueResponse::from).toList());
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/observations")
    public ResponseEntity<ObservationBiologiqueResponse> create(@PathVariable UUID patientId,
                                                                @RequestBody @Valid CreateObservationRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        ConceptCode analyte = ConceptCode.of(
                CodingSystem.valueOf(request.codeSystem()), request.code(), request.codeDisplay());
        ValeurMesuree valeurNum = request.valeurNum() == null ? null
                : new ValeurMesuree(request.valeurNum(), request.unite());
        ObservationBiologique observation = useCase.create(center, patientId, request.demandeExamenId(), analyte,
                valeurNum, request.valeurTexte(), request.datePrelevement(),
                request.statut() == null ? null : StatutObservation.valueOf(request.statut()));
        return ResponseEntity.ok(ObservationBiologiqueResponse.from(observation));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/observations/{observationId}")
    public ResponseEntity<ObservationBiologiqueResponse> corriger(@PathVariable UUID patientId,
                                                                  @PathVariable UUID observationId,
                                                                  @RequestBody @Valid CorrigerObservationRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        ValeurMesuree valeurNum = request.valeurNum() == null ? null
                : new ValeurMesuree(request.valeurNum(), request.unite());
        ObservationBiologique observation = useCase.corriger(center, patientId, observationId, valeurNum, request.valeurTexte());
        return ResponseEntity.ok(ObservationBiologiqueResponse.from(observation));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @DeleteMapping("/{patientId}/observations/{observationId}")
    public ResponseEntity<Void> delete(@PathVariable UUID patientId,
                                       @PathVariable UUID observationId,
                                       @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        useCase.delete(center, patientId, observationId);
        return ResponseEntity.noContent().build();
    }
}
