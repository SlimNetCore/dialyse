package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.medical.greffe.port.BilanPreGreffeUseCase;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.AvisRcp;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.StatutBilanGreffe;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.request.ChangerStatutBilanGreffeRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateDecisionRcpRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertBilanImmunologiqueRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertNotesBilanGreffeRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.BilanPreGreffeResponse;
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
 * Bilan de préparation à la greffe rénale du patient receveur : statut d'éligibilité, bilan
 * immunologique (groupe sanguin confirmé, typage HLA, PRA), notes et décisions de RCP.
 * <p>
 * Accès réservé au corps médical : le MEDECIN écrit, l'ADMIN consulte.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class BilanPreGreffeRestController {

    private final BilanPreGreffeUseCase useCase;
    private final CenterAccessGuard centerAccessGuard;

    public BilanPreGreffeRestController(BilanPreGreffeUseCase useCase, CenterAccessGuard centerAccessGuard) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/greffe/bilan")
    public ResponseEntity<BilanPreGreffeResponse> get(@PathVariable UUID patientId,
                                                      @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        return ResponseEntity.ok(BilanPreGreffeResponse.from(useCase.getOrCreate(center, patientId)));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/greffe/bilan/statut")
    public ResponseEntity<BilanPreGreffeResponse> changerStatut(@PathVariable UUID patientId,
                                                                @RequestBody ChangerStatutBilanGreffeRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        var bilan = useCase.changerStatut(center, patientId, StatutBilanGreffe.valueOf(request.statut()));
        return ResponseEntity.ok(BilanPreGreffeResponse.from(bilan));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/greffe/bilan/immunologique")
    public ResponseEntity<BilanPreGreffeResponse> mettreAJourBilanImmunologique(
            @PathVariable UUID patientId, @RequestBody UpsertBilanImmunologiqueRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        var bilan = useCase.mettreAJourBilanImmunologique(center, patientId, request.groupeSanguinConfirme(),
                request.typageHla(), request.praClasseI(), request.praClasseII());
        return ResponseEntity.ok(BilanPreGreffeResponse.from(bilan));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/greffe/bilan/notes")
    public ResponseEntity<BilanPreGreffeResponse> mettreAJourNotes(@PathVariable UUID patientId,
                                                                   @RequestBody UpsertNotesBilanGreffeRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        var bilan = useCase.mettreAJourNotes(center, patientId, request.contreIndications(),
                request.conclusionNephrologue());
        return ResponseEntity.ok(BilanPreGreffeResponse.from(bilan));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/greffe/bilan/decisions-rcp")
    public ResponseEntity<BilanPreGreffeResponse> ajouterDecisionRcp(@PathVariable UUID patientId,
                                                                     @RequestBody CreateDecisionRcpRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        var bilan = useCase.ajouterDecisionRcp(center, patientId, request.dateReunion(),
                AvisRcp.valueOf(request.avis()), request.compteRendu(), request.prochaineDateRevue());
        return ResponseEntity.ok(BilanPreGreffeResponse.from(bilan));
    }
}
