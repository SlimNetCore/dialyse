package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertVoletParamedicalRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/seances")
public class VoletParamedicalRestController {

    private final VoletParamedicalUseCase voletParamedicalUseCase;
    private final SeanceUseCase seanceUseCase;
    private final NotificationService notificationService;

    public VoletParamedicalRestController(VoletParamedicalUseCase voletParamedicalUseCase,
                                          SeanceUseCase seanceUseCase,
                                          NotificationService notificationService) {
        this.voletParamedicalUseCase = voletParamedicalUseCase;
        this.seanceUseCase = seanceUseCase;
        this.notificationService = notificationService;
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','SECRETAIRE')")
    @PutMapping("/{seanceId}/volet-paramedical")
    public ResponseEntity<?> upsert(@PathVariable UUID seanceId,
                                    @RequestBody @Valid UpsertVoletParamedicalRequest request) {
        var volet = voletParamedicalUseCase.save(
                CenterId.of(request.centerId()),
                seanceId,
                request.poidsAvantKg(),
                request.poidsApresKg(),
                request.taAvant(),
                request.taApres(),
                request.dureeMinutes(),
                request.debitSangMlMin(),
                request.ultrafiltrationMl(),
                request.anticoagulant(),
                request.typeDialysat(),
                request.incidents()
        );

        var details = seanceUseCase.getDetails(CenterId.of(request.centerId()), seanceId);
        var patient = details.patient();
        var seance = details.seance();
        notificationService.notifySeanceParamedicalSaved(
                request.centerId(),
                seanceId,
                seance.getPatientId(),
                patient.getNom(),
                patient.getPrenom(),
                seance.getDateSeance() == null ? null : seance.getDateSeance().toString()
        );

        return ResponseEntity.ok(Map.of(
                "id", volet.getId(),
                "seanceId", volet.getSeanceId(),
                "updatedAt", volet.getUpdatedAt()
        ));
    }
}
