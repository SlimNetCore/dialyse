package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.medical.greffe.port.EtapeBilanPreGreffeUseCase;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.CategorieEtapeGreffe;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.StatutEtapeGreffe;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateEtapeBilanGreffeRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpdateEtapeBilanGreffeRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.EtapeBilanGreffeResponse;
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
 * Checklist du bilan pré-greffe rénale (receveur) : liste d'étapes (examens, consultations,
 * sérologies...) à réaliser, avec génération d'une liste standard prédéfinie et ajout/retrait
 * libre par le médecin.
 * <p>
 * L'infirmier peut planifier/cocher une étape réalisée ; les décisions cliniques restent au
 * médecin (statut du bilan, décisions de RCP — voir {@code BilanPreGreffeRestController}).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class EtapeBilanPreGreffeRestController {

    private final EtapeBilanPreGreffeUseCase useCase;
    private final CenterAccessGuard centerAccessGuard;

    public EtapeBilanPreGreffeRestController(EtapeBilanPreGreffeUseCase useCase, CenterAccessGuard centerAccessGuard) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN','INFIRMIER')")
    @GetMapping("/{patientId}/greffe/etapes")
    public ResponseEntity<List<EtapeBilanGreffeResponse>> list(@PathVariable UUID patientId,
                                                               @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        return ResponseEntity.ok(useCase.listByPatient(center, patientId).stream()
                .map(EtapeBilanGreffeResponse::from).toList());
    }

    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER')")
    @PostMapping("/{patientId}/greffe/etapes")
    public ResponseEntity<EtapeBilanGreffeResponse> create(@PathVariable UUID patientId,
                                                           @RequestBody CreateEtapeBilanGreffeRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        var etape = useCase.create(center, patientId, CategorieEtapeGreffe.valueOf(request.categorie()),
                request.libelle());
        return ResponseEntity.ok(EtapeBilanGreffeResponse.from(etape));
    }

    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER')")
    @PostMapping("/{patientId}/greffe/etapes/generer-standard")
    public ResponseEntity<List<EtapeBilanGreffeResponse>> genererEtapesStandard(
            @PathVariable UUID patientId, @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        return ResponseEntity.ok(useCase.genererEtapesStandard(center, patientId).stream()
                .map(EtapeBilanGreffeResponse::from).toList());
    }

    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER')")
    @PutMapping("/{patientId}/greffe/etapes/{etapeId}")
    public ResponseEntity<EtapeBilanGreffeResponse> update(@PathVariable UUID patientId, @PathVariable UUID etapeId,
                                                           @RequestBody UpdateEtapeBilanGreffeRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        var etape = useCase.update(center, patientId, etapeId, StatutEtapeGreffe.valueOf(request.statut()),
                request.dateRealisation(), request.resultat(), request.dateExpiration(), request.demandeExamenId(),
                request.serologieId());
        return ResponseEntity.ok(EtapeBilanGreffeResponse.from(etape));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @DeleteMapping("/{patientId}/greffe/etapes/{etapeId}")
    public ResponseEntity<Void> delete(@PathVariable UUID patientId, @PathVariable UUID etapeId,
                                       @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        useCase.delete(center, patientId, etapeId);
        return ResponseEntity.noContent().build();
    }
}
