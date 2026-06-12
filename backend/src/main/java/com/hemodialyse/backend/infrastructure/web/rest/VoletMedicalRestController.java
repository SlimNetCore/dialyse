package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.port.VoletMedicalUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertVoletMedicalRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/seances")
public class VoletMedicalRestController {

    private final VoletMedicalUseCase voletMedicalUseCase;

    public VoletMedicalRestController(VoletMedicalUseCase voletMedicalUseCase) {
        this.voletMedicalUseCase = voletMedicalUseCase;
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
    @PutMapping("/{seanceId}/volet-medical")
    public ResponseEntity<?> upsert(@PathVariable UUID seanceId,
                                    @RequestBody @Valid UpsertVoletMedicalRequest request) {
        var volet = voletMedicalUseCase.save(
                CenterId.of(request.centerId()),
                seanceId,
                request.prescription(),
                request.toleranceSeance(),
                request.examenClinique(),
                request.resultatsBiologiques(),
                request.ajustementsTherapeutiques(),
                request.conclusionMedicale()
        );

        return ResponseEntity.ok(Map.of(
                "id", volet.getId(),
                "seanceId", volet.getSeanceId(),
                "updatedAt", volet.getUpdatedAt()
        ));
    }
}

