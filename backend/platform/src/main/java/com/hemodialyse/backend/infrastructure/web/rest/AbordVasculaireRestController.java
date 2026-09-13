package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.port.AbordVasculaireUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertAbordVasculaireRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.AbordVasculaireResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.EntityWriteResponse;
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
 * Historique des abords vasculaires du patient (FAV, PTFE, cathéters).
 * <p>
 * Accès réservé au corps médical : le MEDECIN écrit, l'ADMIN consulte.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class AbordVasculaireRestController {

    private final AbordVasculaireUseCase useCase;
    private final CenterAccessGuard centerAccessGuard;

    public AbordVasculaireRestController(AbordVasculaireUseCase useCase,
                                         CenterAccessGuard centerAccessGuard) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
    }

    /**
     * Lecture ouverte à l'infirmier : c'est lui qui ponctionne l'abord. Connaître son type et son
     * côté est une nécessité de soin. L'écriture reste réservée au médecin.
     */
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN','INFIRMIER')")
    @GetMapping("/{patientId}/abords-vasculaires")
    public ResponseEntity<PagedResponse<AbordVasculaireResponse>> list(
            @PathVariable UUID patientId,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        var paged = useCase.listPagedByPatient(center, patientId, page, size);
        return ResponseEntity.ok(PagedResponse.from(paged, AbordVasculaireResponse::from));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/abords-vasculaires")
    public ResponseEntity<EntityWriteResponse> create(@PathVariable UUID patientId,
                                                      @RequestBody @Valid UpsertAbordVasculaireRequest request) {
        return ResponseEntity.ok(save(patientId, null, request));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/abords-vasculaires/{abordId}")
    public ResponseEntity<EntityWriteResponse> update(@PathVariable UUID patientId,
                                                      @PathVariable UUID abordId,
                                                      @RequestBody @Valid UpsertAbordVasculaireRequest request) {
        return ResponseEntity.ok(save(patientId, abordId, request));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @DeleteMapping("/{patientId}/abords-vasculaires/{abordId}")
    public ResponseEntity<Void> delete(@PathVariable UUID patientId,
                                       @PathVariable UUID abordId,
                                       @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        useCase.delete(center, patientId, abordId);
        return ResponseEntity.noContent().build();
    }

    private EntityWriteResponse save(UUID patientId, UUID abordId, UpsertAbordVasculaireRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        var abord = useCase.save(
                center,
                patientId,
                abordId,
                request.typeAbord(),
                request.cote(),
                request.localisation(),
                request.dateCreation(),
                request.dateFin(),
                request.actif(),
                request.complications()
        );
        return new EntityWriteResponse(abord.getId(), abord.getPatientId(), abord.getCreatedAt());
    }
}
