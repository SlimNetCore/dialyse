package com.hemodialyse.backend.infrastructure.web.rest.gmao;

import com.hemodialyse.backend.domain.gmao.model.*;
import com.hemodialyse.backend.domain.gmao.port.EquipementRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.EquipementStatutHistoriqueRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.InterventionRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.PlanMaintenanceRepositoryPort;
import com.hemodialyse.backend.domain.gmao.service.IndisponibiliteCalculator;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.response.gmao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

/**
 * Endpoints d'aide à la décision sur un équipement GMAO : fiche détaillée (coût, indisponibilité,
 * historique) et vérification de disponibilité avant affectation d'un patient (module GMAO v2).
 * Contrôleur dédié (SRP, AGENTS.md §5) plutôt que d'alourdir {@code EquipementRestController}.
 */
@RestController
@RequestMapping("/api/v1/gmao/equipements/{id}")
@PreAuthorize("hasRole('ADMIN')")
public class EquipementFicheRestController {

    private static final Set<StatutEquipement> STATUTS_INDISPONIBLES_PATIENT = Set.of(
            StatutEquipement.EN_MAINTENANCE, StatutEquipement.EN_ATTENTE_PIECE,
            StatutEquipement.HORS_SERVICE, StatutEquipement.REFORME);

    private final EquipementRepositoryPort equipementRepository;
    private final InterventionRepositoryPort interventionRepository;
    private final PlanMaintenanceRepositoryPort planRepository;
    private final EquipementStatutHistoriqueRepositoryPort historiqueRepository;

    public EquipementFicheRestController(
            EquipementRepositoryPort equipementRepository,
            InterventionRepositoryPort interventionRepository,
            PlanMaintenanceRepositoryPort planRepository,
            EquipementStatutHistoriqueRepositoryPort historiqueRepository) {
        this.equipementRepository = equipementRepository;
        this.interventionRepository = interventionRepository;
        this.planRepository = planRepository;
        this.historiqueRepository = historiqueRepository;
    }

    /**
     * Fiche détaillée de l'équipement sur les 12 derniers mois (coût cumulé de maintenance, temps
     * d'indisponibilité, dernière intervention, prochaine maintenance planifiée).
     */
    @GetMapping("/fiche")
    public ResponseEntity<EquipementFicheResponse> fiche(@PathVariable String id, Authentication authentication) {
        Equipement equipement = requireEquipement(id, authentication);
        UUID equipementId = equipement.getId();

        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusMonths(12);

        long nbInterventions = interventionRepository.countByEquipementId(equipementId);
        Duration indisponibilite = IndisponibiliteCalculator.calculer(
                historiqueRepository.findByEquipementIdOrderByChangedAtAsc(equipementId),
                equipement.getStatut(), from, to);
        var coutPeriode = interventionRepository.sumCoutByEquipementIdAndDateRange(equipementId, from, to);
        InterventionResponse derniere = interventionRepository.findLatestByEquipementId(equipementId)
                .map(InterventionResponse::new).orElse(null);
        PlanMaintenanceResponse prochainPlan = planRepository.findActiveByEquipementId(equipementId).stream()
                .filter(p -> p.getProchaineDatePrevue() != null)
                .min(Comparator.comparing(PlanMaintenance::getProchaineDatePrevue))
                .map(PlanMaintenanceResponse::new)
                .orElse(null);

        return ResponseEntity.ok(EquipementFicheResponse.of(
                new EquipementResponse(equipement), nbInterventions,
                indisponibilite.toMinutes() / 60.0, coutPeriode, derniere, prochainPlan));
    }

    /**
     * Vérification de disponibilité avant d'affecter un patient à ce générateur — avertissement non
     * bloquant (voir le wizard patient / l'affichage séance, qui l'utilisent pour un simple rappel).
     */
    @GetMapping("/disponibilite-patient")
    public ResponseEntity<DisponibilitePatientResponse> disponibilitePatient(
            @PathVariable String id, Authentication authentication) {
        Equipement equipement = requireEquipement(id, authentication);

        UUID interventionEnCoursId = interventionRepository.findPendingByEquipementId(equipement.getId()).stream()
                .filter(i -> i.getStatut() == StatutIntervention.EN_COURS)
                .map(Intervention::getId)
                .findFirst()
                .orElse(null);

        boolean disponible = !STATUTS_INDISPONIBLES_PATIENT.contains(equipement.getStatut())
                && interventionEnCoursId == null;

        return ResponseEntity.ok(new DisponibilitePatientResponse(disponible, equipement.getStatut(), interventionEnCoursId));
    }

    private Equipement requireEquipement(String id, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UUID centreId = UUID.fromString(principal.getCenterId());
        return equipementRepository.findById(UUID.fromString(id))
                .filter(e -> e.getCentreId().equals(centreId))
                .orElseThrow(() -> new IllegalArgumentException("Équipement non trouvé"));
    }
}
