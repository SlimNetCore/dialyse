package com.hemodialyse.backend.infrastructure.web.rest.gmao;

import com.hemodialyse.backend.domain.gmao.model.*;
import com.hemodialyse.backend.domain.gmao.port.InterventionRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.request.gmao.*;
import com.hemodialyse.backend.infrastructure.web.dto.response.gmao.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller pour la gestion des interventions GMAO.
 * Isolation multi-centre systématique (AGENTS.md §2). Liste obligatoirement paginée (AGENTS.md §9).
 */
@RestController
@RequestMapping("/api/v1/gmao/interventions")
@PreAuthorize("hasRole('ADMIN')")
public class InterventionRestController {

    private final InterventionRepositoryPort interventionRepository;

    public InterventionRestController(InterventionRepositoryPort interventionRepository) {
        this.interventionRepository = interventionRepository;
    }

    /**
     * Crée une nouvelle intervention
     */
    @PostMapping
    public ResponseEntity<InterventionResponse> creerIntervention(
            @Valid @RequestBody CreateInterventionRequest request,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        Intervention intervention = Intervention.creer(
                request.equipementId(),
                UUID.fromString(principal.getCenterId()),
                TypeIntervention.valueOf(request.type()),
                request.dateDebut(),
                request.description(),
                request.technicienId(),
                UUID.fromString(principal.getId())
        );

        interventionRepository.save(intervention);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new InterventionResponse(intervention));
    }

    /**
     * Récupère une intervention par ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<InterventionResponse> obtenirIntervention(@PathVariable String id) {
        return interventionRepository.findById(UUID.fromString(id))
                .map(InterventionResponse::new)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Liste paginée des interventions du centre courant, filtrable par statut et/ou équipement
     * (AGENTS.md §9 : toute liste doit être paginée — jamais de findAll sans limite).
     */
    @GetMapping
    public ResponseEntity<PagedResult<InterventionResponse>> listerInterventions(
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) UUID equipementId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        PagedResult<Intervention> paged = equipementId != null
                ? interventionRepository.findPagedByEquipementId(equipementId, page, size)
                : interventionRepository.findPaged(UUID.fromString(principal.getCenterId()), statut, page, size);

        return ResponseEntity.ok(new PagedResult<>(
                paged.items().stream().map(InterventionResponse::new).toList(),
                paged.total(),
                paged.page(),
                paged.size()
        ));
    }

    /**
     * Démarre une intervention
     */
    @PostMapping("/{id}/demarrer")
    public ResponseEntity<InterventionResponse> demarrerIntervention(
            @PathVariable String id,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        Intervention intervention = interventionRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new IllegalArgumentException("Intervention non trouvée"));

        intervention.demarrer(UUID.fromString(principal.getId()));
        interventionRepository.save(intervention);

        return ResponseEntity.ok(new InterventionResponse(intervention));
    }

    /**
     * Termine une intervention
     */
    @PostMapping("/{id}/terminer")
    public ResponseEntity<InterventionResponse> terminerIntervention(
            @PathVariable String id,
            @Valid @RequestBody TerminerInterventionRequest request,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        Intervention intervention = interventionRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new IllegalArgumentException("Intervention non trouvée"));

        intervention.terminer(request.actions(), UUID.fromString(principal.getId()));
        interventionRepository.save(intervention);

        return ResponseEntity.ok(new InterventionResponse(intervention));
    }

    /**
     * Annule une intervention
     */
    @PostMapping("/{id}/annuler")
    public ResponseEntity<InterventionResponse> annulerIntervention(
            @PathVariable String id,
            @Valid @RequestBody AnnulerInterventionRequest request,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        Intervention intervention = interventionRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new IllegalArgumentException("Intervention non trouvée"));

        intervention.annuler(request.raison(), UUID.fromString(principal.getId()));
        interventionRepository.save(intervention);

        return ResponseEntity.ok(new InterventionResponse(intervention));
    }
}
