package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.medical.examen.aggregate.DemandeExamen;
import com.hemodialyse.backend.domain.medical.examen.entity.LigneDemandeExamen;
import com.hemodialyse.backend.domain.medical.examen.port.DemandeExamenUseCase;
import com.hemodialyse.backend.domain.medical.examen.valueobject.CategorieExamen;
import com.hemodialyse.backend.domain.medical.shared.valueobject.CodingSystem;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateDemandeExamenRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.LigneDemandeExamenRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.TransitionDemandeExamenRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.DemandeExamenResponse;
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
 * Demandes d'examen du patient (biologie, imagerie, fonctionnel, anapath).
 * <p>
 * Accès réservé au corps médical : le MEDECIN écrit, l'ADMIN consulte.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class DemandeExamenRestController {

    private final DemandeExamenUseCase useCase;
    private final CenterAccessGuard centerAccessGuard;

    public DemandeExamenRestController(DemandeExamenUseCase useCase, CenterAccessGuard centerAccessGuard) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/demandes-examen")
    public ResponseEntity<PagedResponse<DemandeExamenResponse>> list(
            @PathVariable UUID patientId,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        var paged = useCase.listPagedByPatient(center, patientId, page, size);
        return ResponseEntity.ok(PagedResponse.from(paged, DemandeExamenResponse::from));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/demandes-examen")
    public ResponseEntity<DemandeExamenResponse> create(@PathVariable UUID patientId,
                                                        @RequestBody @Valid CreateDemandeExamenRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        var lignes = request.lignes().stream().map(this::toLigne).toList();
        DemandeExamen demande = useCase.create(center, patientId, request.prescripteurId(), request.dateDemande(),
                CategorieExamen.valueOf(request.categorie()), request.urgent(), request.motif(), lignes);
        return ResponseEntity.ok(DemandeExamenResponse.from(demande));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/demandes-examen/{demandeId}/preleve")
    public ResponseEntity<DemandeExamenResponse> preleve(@PathVariable UUID patientId,
                                                         @PathVariable UUID demandeId,
                                                         @RequestBody @Valid TransitionDemandeExamenRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        return ResponseEntity.ok(DemandeExamenResponse.from(useCase.preleve(center, patientId, demandeId)));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/demandes-examen/{demandeId}/resultat-disponible")
    public ResponseEntity<DemandeExamenResponse> marquerResultatDisponible(
            @PathVariable UUID patientId, @PathVariable UUID demandeId,
            @RequestBody @Valid TransitionDemandeExamenRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        return ResponseEntity.ok(DemandeExamenResponse.from(useCase.marquerResultatDisponible(center, patientId, demandeId)));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/demandes-examen/{demandeId}/valider")
    public ResponseEntity<DemandeExamenResponse> valider(@PathVariable UUID patientId,
                                                         @PathVariable UUID demandeId,
                                                         @RequestBody @Valid TransitionDemandeExamenRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        return ResponseEntity.ok(DemandeExamenResponse.from(
                useCase.valider(center, patientId, demandeId, request.conclusion())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/demandes-examen/{demandeId}/annuler")
    public ResponseEntity<DemandeExamenResponse> annuler(@PathVariable UUID patientId,
                                                         @PathVariable UUID demandeId,
                                                         @RequestBody @Valid TransitionDemandeExamenRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        return ResponseEntity.ok(DemandeExamenResponse.from(useCase.annuler(center, patientId, demandeId)));
    }

    private LigneDemandeExamen toLigne(LigneDemandeExamenRequest request) {
        ConceptCode analyte = request.code() == null || request.code().isBlank() ? null
                : ConceptCode.of(CodingSystem.valueOf(request.codeSystem()), request.code(), request.codeDisplay());
        return LigneDemandeExamen.creer(analyte, request.libelle(), request.commentaire());
    }
}
