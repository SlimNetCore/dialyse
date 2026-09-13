package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.medical.ordonnance.aggregate.Ordonnance;
import com.hemodialyse.backend.domain.medical.ordonnance.entity.LigneOrdonnance;
import com.hemodialyse.backend.domain.medical.ordonnance.port.OrdonnanceUseCase;
import com.hemodialyse.backend.domain.medical.shared.valueobject.CodingSystem;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateOrdonnanceRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.LigneOrdonnanceRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.TransitionOrdonnanceRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.OrdonnanceResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.PagedResponse;
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
 * Ordonnances médicamenteuses du patient — indépendantes des paramètres de séance
 * ({@code PrescriptionMedicale}, bc-seance) : un document destiné au patient/à la pharmacie.
 * <p>
 * Accès réservé au corps médical : le MEDECIN écrit, l'ADMIN consulte. Une fois signée, une
 * ordonnance est immuable — corriger son contenu impose de l'annuler et d'en créer une nouvelle.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class OrdonnanceRestController {

    private final OrdonnanceUseCase useCase;
    private final CenterAccessGuard centerAccessGuard;

    public OrdonnanceRestController(OrdonnanceUseCase useCase, CenterAccessGuard centerAccessGuard) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/ordonnances")
    public ResponseEntity<PagedResponse<OrdonnanceResponse>> list(
            @PathVariable UUID patientId,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        var paged = useCase.listPagedByPatient(center, patientId, page, size);
        return ResponseEntity.ok(PagedResponse.from(paged, OrdonnanceResponse::from));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/ordonnances")
    public ResponseEntity<OrdonnanceResponse> create(@PathVariable UUID patientId,
                                                     @RequestBody @Valid CreateOrdonnanceRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        var lignes = request.lignes().stream().map(this::toLigne).toList();
        Ordonnance ordonnance = useCase.create(center, patientId, request.medecinId(), request.datePrescription(), lignes);
        return ResponseEntity.ok(OrdonnanceResponse.from(ordonnance));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/ordonnances/{ordonnanceId}/signer")
    public ResponseEntity<OrdonnanceResponse> signer(@PathVariable UUID patientId, @PathVariable UUID ordonnanceId,
                                                     @RequestBody @Valid TransitionOrdonnanceRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        return ResponseEntity.ok(OrdonnanceResponse.from(useCase.signer(center, patientId, ordonnanceId)));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/ordonnances/{ordonnanceId}/imprimer")
    public ResponseEntity<OrdonnanceResponse> marquerImprimee(@PathVariable UUID patientId,
                                                              @PathVariable UUID ordonnanceId,
                                                              @RequestBody @Valid TransitionOrdonnanceRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        return ResponseEntity.ok(OrdonnanceResponse.from(useCase.marquerImprimee(center, patientId, ordonnanceId)));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/ordonnances/{ordonnanceId}/annuler")
    public ResponseEntity<OrdonnanceResponse> annuler(@PathVariable UUID patientId, @PathVariable UUID ordonnanceId,
                                                      @RequestBody @Valid TransitionOrdonnanceRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        return ResponseEntity.ok(OrdonnanceResponse.from(useCase.annuler(center, patientId, ordonnanceId)));
    }

    private LigneOrdonnance toLigne(LigneOrdonnanceRequest request) {
        ConceptCode medicament = request.code() == null || request.code().isBlank() ? null
                : ConceptCode.of(CodingSystem.valueOf(request.codeSystem()), request.code(), request.codeDisplay());
        return LigneOrdonnance.creer(medicament, request.libelle(), request.posologie(), request.voie(),
                request.dureeJours(), request.quantite(), request.instructions());
    }
}
