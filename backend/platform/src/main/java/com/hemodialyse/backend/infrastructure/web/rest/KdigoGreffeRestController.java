package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.query.KdigoGreffeQueryService;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.GardeDePatient;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.SousLaGardeDuDossierMedical;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.response.KdigoGreffeResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Évaluations KDIGO du bilan pré-greffe rénale : risque immunologique (PRA), fonction rénale
 * résiduelle estimée, points d'attention sérologiques — des aides à la décision recalculées à la
 * demande, pas un verdict d'éligibilité (la décision reste à la RCP / au médecin).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class KdigoGreffeRestController implements SousLaGardeDuDossierMedical {

    private final KdigoGreffeQueryService queryService;
    private final CenterAccessGuard centerAccessGuard;
    private final GardeDePatient patients;

    public KdigoGreffeRestController(KdigoGreffeQueryService queryService, CenterAccessGuard centerAccessGuard,
                                     GardeDePatient patients) {
        this.queryService = queryService;
        this.centerAccessGuard = centerAccessGuard;
        this.patients = patients;
    }

    /** DM7 (KB-MED-86) : un patient inconnu ou d'un autre centre est refusé en 404 {@code PATIENT_INTROUVABLE} ; calculs inchangés. */
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/greffe/kdigo")
    public ResponseEntity<KdigoGreffeResponse> get(@PathVariable UUID patientId,
                                                   @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        patients.exiger(center.value(), patientId);
        return ResponseEntity.ok(queryService.evaluer(center, patientId));
    }
}
