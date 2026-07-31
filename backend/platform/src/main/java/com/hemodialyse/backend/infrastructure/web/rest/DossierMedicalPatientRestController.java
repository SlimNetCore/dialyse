package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.model.DossierMedicalPatient;
import com.hemodialyse.backend.domain.seance.port.DossierMedicalPatientUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertDossierMedicalPatientRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
public class DossierMedicalPatientRestController {

    private final DossierMedicalPatientUseCase useCase;

    public DossierMedicalPatientRestController(DossierMedicalPatientUseCase useCase) {
        this.useCase = useCase;
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/{patientId}/dossier-medical")
    public ResponseEntity<?> get(@PathVariable UUID patientId, @RequestParam UUID centerId) {
        return useCase.getByPatient(CenterId.of(centerId), patientId)
                .<ResponseEntity<?>>map(d -> ResponseEntity.ok(toResponse(d)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
    @PostMapping("/{patientId}/dossier-medical")
    public ResponseEntity<?> create(@PathVariable UUID patientId,
                                    @RequestBody @Valid UpsertDossierMedicalPatientRequest request) {
        var dossier = useCase.upsert(
                CenterId.of(request.centerId()),
                patientId,
                request.nephropathieInitiale(),
                request.dateMiseEnDialyse(),
                request.hepatiteBStatut(),
                request.hepatiteCStatut(),
                request.observationGlobale()
        );
        return ResponseEntity.ok(toWriteResponse(dossier));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
    @PutMapping("/{patientId}/dossier-medical")
    public ResponseEntity<?> update(@PathVariable UUID patientId,
                                    @RequestBody @Valid UpsertDossierMedicalPatientRequest request) {
        var dossier = useCase.upsert(
                CenterId.of(request.centerId()),
                patientId,
                request.nephropathieInitiale(),
                request.dateMiseEnDialyse(),
                request.hepatiteBStatut(),
                request.hepatiteCStatut(),
                request.observationGlobale()
        );
        return ResponseEntity.ok(toWriteResponse(dossier));
    }

    private Map<String, Object> toResponse(DossierMedicalPatient d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("patientId", d.getPatientId());
        m.put("centerId", d.getCenterId());
        m.put("nephropathieInitiale", d.getNephropathieInitiale());
        m.put("dateMiseEnDialyse", d.getDateMiseEnDialyse());
        m.put("hepatiteBStatut", d.getHepatiteBStatut());
        m.put("hepatiteCStatut", d.getHepatiteCStatut());
        m.put("observationGlobale", d.getObservationGlobale());
        m.put("createdAt", d.getCreatedAt());
        m.put("updatedAt", d.getUpdatedAt());
        return m;
    }

    private Map<String, Object> toWriteResponse(DossierMedicalPatient d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("patientId", d.getPatientId());
        m.put("updatedAt", d.getUpdatedAt());
        return m;
    }
}


