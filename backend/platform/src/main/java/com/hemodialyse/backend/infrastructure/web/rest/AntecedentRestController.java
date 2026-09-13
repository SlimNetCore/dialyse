package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.medical.antecedent.aggregate.Antecedent;
import com.hemodialyse.backend.domain.medical.antecedent.port.AntecedentUseCase;
import com.hemodialyse.backend.domain.medical.antecedent.valueobject.StatutClinique;
import com.hemodialyse.backend.domain.medical.antecedent.valueobject.TypeAntecedent;
import com.hemodialyse.backend.domain.medical.shared.valueobject.CodingSystem;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.request.ResoudreAntecedentRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertAntecedentRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.AntecedentResponse;
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

import java.util.UUID;

/**
 * Antécédents et comorbidités du patient, codés CIM-10 quand possible.
 * <p>
 * Accès réservé au corps médical : le MEDECIN écrit, l'ADMIN consulte.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class AntecedentRestController {

    private final AntecedentUseCase useCase;
    private final CenterAccessGuard centerAccessGuard;

    public AntecedentRestController(AntecedentUseCase useCase, CenterAccessGuard centerAccessGuard) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/antecedents")
    public ResponseEntity<PagedResponse<AntecedentResponse>> list(
            @PathVariable UUID patientId,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        var paged = useCase.listPagedByPatient(center, patientId, page, size);
        return ResponseEntity.ok(PagedResponse.from(paged, AntecedentResponse::from));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/antecedents")
    public ResponseEntity<AntecedentResponse> create(@PathVariable UUID patientId,
                                                     @RequestBody @Valid UpsertAntecedentRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        Antecedent antecedent = useCase.create(
                center, patientId, TypeAntecedent.valueOf(request.type()), toConceptCode(request),
                request.libelleLibre(), request.dateDebut(), request.dateFin(), request.severite(), request.note());
        return ResponseEntity.ok(AntecedentResponse.from(antecedent));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/antecedents/{antecedentId}")
    public ResponseEntity<AntecedentResponse> update(@PathVariable UUID patientId,
                                                     @PathVariable UUID antecedentId,
                                                     @RequestBody @Valid UpsertAntecedentRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        Antecedent antecedent = useCase.update(
                center, patientId, antecedentId, toConceptCode(request), request.libelleLibre(),
                request.dateDebut(), request.dateFin(),
                request.statutClinique() == null ? null : StatutClinique.valueOf(request.statutClinique()),
                request.severite(), request.note());
        return ResponseEntity.ok(AntecedentResponse.from(antecedent));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/antecedents/{antecedentId}/resoudre")
    public ResponseEntity<AntecedentResponse> resoudre(@PathVariable UUID patientId,
                                                       @PathVariable UUID antecedentId,
                                                       @RequestBody @Valid ResoudreAntecedentRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        Antecedent antecedent = useCase.resoudre(center, patientId, antecedentId, request.dateResolution());
        return ResponseEntity.ok(AntecedentResponse.from(antecedent));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @DeleteMapping("/{patientId}/antecedents/{antecedentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID patientId,
                                       @PathVariable UUID antecedentId,
                                       @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        useCase.delete(center, patientId, antecedentId);
        return ResponseEntity.noContent().build();
    }

    private ConceptCode toConceptCode(UpsertAntecedentRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            return null;
        }
        return ConceptCode.of(CodingSystem.valueOf(request.codeSystem()), request.code(), request.codeDisplay());
    }
}
