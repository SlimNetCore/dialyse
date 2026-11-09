package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.EvaluerLeCandidatALaGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.EvaluationKdigoReponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adapter entrant HTTP de l'évaluation KDIGO du candidat à la greffe : MÊME URL, MÊMES champs JSON et MÊMES droits que
 * {@code KdigoGreffeRestController} (plateforme, retiré par DM9, ADR 0101) : {@code MEDECIN}, {@code ADMIN} ; patient inconnu ou d'un autre
 * centre 404 {@code PATIENT_INTROUVABLE} (DM7) ; centre d'une autre session 403 ; 403 avant 400 et 400 stable par l'advice du contexte.
 * Des aides à la décision recalculées à la demande, pas un verdict d'éligibilité.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class EvaluationKdigoControleur {

    private final EvaluerLeCandidatALaGreffe evaluer;

    public EvaluationKdigoControleur(EvaluerLeCandidatALaGreffe evaluer) {
        this.evaluer = evaluer;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/greffe/kdigo")
    public ResponseEntity<EvaluationKdigoReponse> evaluer(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(EvaluationKdigoReponse.de(evaluer.executer(centerId, patientId)));
    }
}
