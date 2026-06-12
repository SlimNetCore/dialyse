package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.model.AbordVasculaire;
import com.hemodialyse.backend.domain.seance.port.AbordVasculaireUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertAbordVasculaireRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
public class AbordVasculaireRestController {

    private final AbordVasculaireUseCase useCase;

    public AbordVasculaireRestController(AbordVasculaireUseCase useCase) {
        this.useCase = useCase;
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/{patientId}/abords-vasculaires")
    public ResponseEntity<?> list(@PathVariable UUID patientId, @RequestParam UUID centerId) {
        var items = useCase.listByPatient(CenterId.of(centerId), patientId).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(items);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
    @PostMapping("/{patientId}/abords-vasculaires")
    public ResponseEntity<?> create(@PathVariable UUID patientId,
                                    @RequestBody @Valid UpsertAbordVasculaireRequest request) {
        var abord = useCase.save(
                CenterId.of(request.centerId()),
                patientId,
                null,
                request.typeAbord(),
                request.cote(),
                request.localisation(),
                request.dateCreation(),
                request.dateFin(),
                request.actif(),
                request.complications()
        );
        return ResponseEntity.ok(toWriteResponse(abord));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
    @PutMapping("/{patientId}/abords-vasculaires/{abordId}")
    public ResponseEntity<?> update(@PathVariable UUID patientId,
                                    @PathVariable UUID abordId,
                                    @RequestBody @Valid UpsertAbordVasculaireRequest request) {
        var abord = useCase.save(
                CenterId.of(request.centerId()),
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
        return ResponseEntity.ok(toWriteResponse(abord));
    }

    private Map<String, Object> toResponse(AbordVasculaire a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("patientId", a.getPatientId());
        m.put("centerId", a.getCenterId());
        m.put("typeAbord", a.getTypeAbord());
        m.put("cote", a.getCote());
        m.put("localisation", a.getLocalisation());
        m.put("dateCreation", a.getDateCreation());
        m.put("dateFin", a.getDateFin());
        m.put("actif", a.getActif());
        m.put("complications", a.getComplications());
        m.put("createdAt", a.getCreatedAt());
        return m;
    }

    private Map<String, Object> toWriteResponse(AbordVasculaire a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("patientId", a.getPatientId());
        m.put("updatedAt", a.getCreatedAt());
        return m;
    }
}




