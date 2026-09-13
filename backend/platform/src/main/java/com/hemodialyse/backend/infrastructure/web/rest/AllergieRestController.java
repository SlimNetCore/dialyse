package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.medical.allergie.aggregate.Allergie;
import com.hemodialyse.backend.domain.medical.allergie.port.AllergieUseCase;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.CategorieAllergie;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.CriticiteAllergie;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.StatutVerificationAllergie;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.TypeReaction;
import com.hemodialyse.backend.domain.medical.shared.valueobject.CodingSystem;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateAllergieRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpdateAllergieRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.AllergieResponse;
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

import java.util.List;
import java.util.UUID;

/**
 * Allergies et intolérances du patient.
 * <p>
 * Accès réservé au corps médical : le MEDECIN écrit, l'ADMIN consulte. Les allergies de
 * criticité haute alimentent le bandeau permanent du dossier ({@link #listCritiques}).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class AllergieRestController {

    private final AllergieUseCase useCase;
    private final CenterAccessGuard centerAccessGuard;

    public AllergieRestController(AllergieUseCase useCase, CenterAccessGuard centerAccessGuard) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/allergies")
    public ResponseEntity<PagedResponse<AllergieResponse>> list(
            @PathVariable UUID patientId,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        var paged = useCase.listPagedByPatient(center, patientId, page, size);
        return ResponseEntity.ok(PagedResponse.from(paged, AllergieResponse::from));
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/allergies/critiques")
    public ResponseEntity<List<AllergieResponse>> listCritiques(@PathVariable UUID patientId,
                                                                @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        return ResponseEntity.ok(useCase.listCritiquesByPatient(center, patientId).stream()
                .map(AllergieResponse::from).toList());
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/allergies")
    public ResponseEntity<AllergieResponse> create(@PathVariable UUID patientId,
                                                   @RequestBody @Valid CreateAllergieRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        ConceptCode substance = ConceptCode.of(
                CodingSystem.valueOf(request.codeSystem()), request.code(), request.codeDisplay());
        Allergie allergie = useCase.create(center, patientId, substance,
                CategorieAllergie.valueOf(request.categorie()), CriticiteAllergie.valueOf(request.criticite()),
                TypeReaction.valueOf(request.typeReaction()), request.manifestations(), request.dateConstatation(),
                request.statutVerification() == null ? null : StatutVerificationAllergie.valueOf(request.statutVerification()));
        return ResponseEntity.ok(AllergieResponse.from(allergie));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/allergies/{allergieId}")
    public ResponseEntity<AllergieResponse> update(@PathVariable UUID patientId,
                                                   @PathVariable UUID allergieId,
                                                   @RequestBody @Valid UpdateAllergieRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        Allergie allergie = useCase.update(center, patientId, allergieId,
                CriticiteAllergie.valueOf(request.criticite()), request.manifestations(),
                request.statutVerification() == null ? null : StatutVerificationAllergie.valueOf(request.statutVerification()));
        return ResponseEntity.ok(AllergieResponse.from(allergie));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @DeleteMapping("/{patientId}/allergies/{allergieId}")
    public ResponseEntity<Void> delete(@PathVariable UUID patientId,
                                       @PathVariable UUID allergieId,
                                       @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        useCase.delete(center, patientId, allergieId);
        return ResponseEntity.noContent().build();
    }
}
