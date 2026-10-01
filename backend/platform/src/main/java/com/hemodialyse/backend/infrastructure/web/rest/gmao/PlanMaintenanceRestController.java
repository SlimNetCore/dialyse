package com.hemodialyse.backend.infrastructure.web.rest.gmao;

import com.hemodialyse.backend.domain.gmao.model.*;
import com.hemodialyse.backend.domain.gmao.port.PlanMaintenanceRepositoryPort;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.request.gmao.*;
import com.hemodialyse.backend.infrastructure.web.dto.response.gmao.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller pour la gestion des plans de maintenance GMAO
 */
@RestController
@RequestMapping("/api/v1/gmao/plans-maintenance")
@PreAuthorize("hasRole('ADMIN')")
public class PlanMaintenanceRestController {

    private final PlanMaintenanceRepositoryPort planRepository;

    public PlanMaintenanceRestController(PlanMaintenanceRepositoryPort planRepository) {
        this.planRepository = planRepository;
    }

    /**
     * Crée un nouveau plan de maintenance
     */
    @PostMapping
    public ResponseEntity<PlanMaintenanceResponse> creerPlan(
            @Valid @RequestBody CreatePlanMaintenanceRequest request,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        PlanMaintenance plan = PlanMaintenance.creer(
                request.equipementId(),
                UUID.fromString(principal.getCenterId()),
                request.designation(),
                request.description(),
                FrequenceMaintenance.valueOf(request.frequence()),
                request.prochaineDatePrevue(),
                request.tachesAEffectuer(),
                UUID.fromString(principal.getId())
        );

        planRepository.save(plan);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new PlanMaintenanceResponse(plan));
    }

    /**
     * Récupère un plan par ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<PlanMaintenanceResponse> obtenirPlan(@PathVariable String id) {
        return planRepository.findById(UUID.fromString(id))
                .map(PlanMaintenanceResponse::new)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Récupère tous les plans du centre
     */
    @GetMapping
    public ResponseEntity<List<PlanMaintenanceResponse>> listerPlans(
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        List<PlanMaintenanceResponse> plans = planRepository
                .findByCentreId(UUID.fromString(principal.getCenterId()))
                .stream()
                .map(PlanMaintenanceResponse::new)
                .toList();

        return ResponseEntity.ok(plans);
    }

    /**
     * Récupère les plans actifs du centre
     */
    @GetMapping("/actifs")
    public ResponseEntity<List<PlanMaintenanceResponse>> listerPlansActifs(
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        List<PlanMaintenanceResponse> plans = planRepository
                .findActiveByCentreId(UUID.fromString(principal.getCenterId()))
                .stream()
                .map(PlanMaintenanceResponse::new)
                .toList();

        return ResponseEntity.ok(plans);
    }

    /**
     * Récupère les plans en retard
     */
    @GetMapping("/en-retard")
    public ResponseEntity<List<PlanMaintenanceResponse>> listerPlansEnRetard(
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        List<PlanMaintenanceResponse> plans = planRepository
                .findOverdueByCentreId(UUID.fromString(principal.getCenterId()), LocalDateTime.now())
                .stream()
                .map(PlanMaintenanceResponse::new)
                .toList();

        return ResponseEntity.ok(plans);
    }

    /**
     * Récupère les plans d'un équipement
     */
    @GetMapping("/equipement/{equipementId}")
    public ResponseEntity<List<PlanMaintenanceResponse>> listerParEquipement(
            @PathVariable String equipementId) {

        List<PlanMaintenanceResponse> plans = planRepository
                .findByEquipementId(UUID.fromString(equipementId))
                .stream()
                .map(PlanMaintenanceResponse::new)
                .toList();

        return ResponseEntity.ok(plans);
    }

    /**
     * Enregistre l'exécution d'un plan
     */
    @PostMapping("/{id}/executer")
    public ResponseEntity<PlanMaintenanceResponse> executerPlan(
            @PathVariable String id,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        PlanMaintenance plan = planRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new IllegalArgumentException("Plan non trouvé"));

        plan.enregistrerExecution(UUID.fromString(principal.getId()));
        planRepository.save(plan);

        return ResponseEntity.ok(new PlanMaintenanceResponse(plan));
    }

    /**
     * Désactive un plan
     */
    @PostMapping("/{id}/desactiver")
    public ResponseEntity<PlanMaintenanceResponse> desactiverPlan(
            @PathVariable String id,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        PlanMaintenance plan = planRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new IllegalArgumentException("Plan non trouvé"));

        plan.desactiver(UUID.fromString(principal.getId()));
        planRepository.save(plan);

        return ResponseEntity.ok(new PlanMaintenanceResponse(plan));
    }

    /**
     * Réactive un plan
     */
    @PostMapping("/{id}/reactiver")
    public ResponseEntity<PlanMaintenanceResponse> reactiverPlan(
            @PathVariable String id,
            @Valid @RequestBody ReactiverPlanRequest request,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        PlanMaintenance plan = planRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new IllegalArgumentException("Plan non trouvé"));

        plan.reactiver(request.nouvelleDatePrevue(), UUID.fromString(principal.getId()));
        planRepository.save(plan);

        return ResponseEntity.ok(new PlanMaintenanceResponse(plan));
    }
}







