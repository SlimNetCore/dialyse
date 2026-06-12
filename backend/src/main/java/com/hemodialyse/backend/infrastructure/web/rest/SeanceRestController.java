package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.model.SeanceArticleConsumption;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateSeanceRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.SignSeanceMedecinRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.ValidateSeanceRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/seances")
public class SeanceRestController {

    private final SeanceUseCase seanceUseCase;

    public SeanceRestController(SeanceUseCase seanceUseCase) {
        this.seanceUseCase = seanceUseCase;
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER')")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody @Valid CreateSeanceRequest request) {
        var seance = seanceUseCase.create(CenterId.of(request.centerId()), request.patientId(), request.dateSeance());
        return ResponseEntity.ok(Map.of(
                "id", seance.getId(),
                "status", seance.getStatus(),
                "dateSeance", seance.getDateSeance()
        ));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER')")
    @PostMapping("/{seanceId}/valider")
    public ResponseEntity<?> validate(@PathVariable UUID seanceId, @RequestBody @Valid ValidateSeanceRequest request) {
        var consommations = request.consommations() == null
                ? java.util.List.<SeanceArticleConsumption>of()
                : request.consommations().stream()
                .map(item -> new SeanceArticleConsumption(item.articleId(), item.quantite()))
                .toList();

        var seance = seanceUseCase.validate(CenterId.of(request.centerId()), seanceId, request.userId(), consommations);
        return ResponseEntity.ok(Map.of(
                "id", seance.getId(),
                "status", seance.getStatus(),
                "validatedAt", seance.getValidatedAt(),
                "signedByInfirmierAt", seance.getSignedByInfirmierAt()
        ));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
    @PostMapping("/{seanceId}/signer-medecin")
    public ResponseEntity<?> signByMedecin(@PathVariable UUID seanceId,
                                           @RequestBody @Valid SignSeanceMedecinRequest request) {
        var seance = seanceUseCase.signByMedecin(CenterId.of(request.centerId()), seanceId, request.userId());
        return ResponseEntity.ok(Map.of(
                "id", seance.getId(),
                "status", seance.getStatus(),
                "signedByMedecinAt", seance.getSignedByMedecinAt()
        ));
    }
}



