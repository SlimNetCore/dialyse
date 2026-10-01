package com.hemodialyse.backend.infrastructure.web.rest.gmao;

import com.hemodialyse.backend.domain.gmao.model.*;
import com.hemodialyse.backend.domain.gmao.port.EquipementRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.EquipementStatutHistoriqueRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.InterventionRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.PlanMaintenanceRepositoryPort;
import com.hemodialyse.backend.domain.gmao.service.AideDecisionMaintenance;
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
            StatutEquipement.HORS_SERVICE, StatutEquipement.A_REFORMER, StatutEquipement.REFORME);

    private final EquipementRepositoryPort equipementRepository;
    private final InterventionRepositoryPort interventionRepository;
    private final PlanMaintenanceRepositoryPort planRepository;
    private final EquipementStatutHistoriqueRepositoryPort historiqueRepository;
    private final AideDecisionMaintenance aideDecision;

    public EquipementFicheRestController(
            EquipementRepositoryPort equipementRepository,
            InterventionRepositoryPort interventionRepository,
            PlanMaintenanceRepositoryPort planRepository,
            EquipementStatutHistoriqueRepositoryPort historiqueRepository,
            AideDecisionMaintenance aideDecision) {
        this.aideDecision = aideDecision;
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

        double heuresIndispo = indisponibilite.toMinutes() / 60.0;
        var coutCumule = interventionRepository.sumCoutCumuleByEquipementId(equipementId);
        var analyse = aideDecision.analyser(
                equipement.getStatut(), equipement.getPrixAcquisition(), coutCumule, coutPeriode, heuresIndispo);

        return ResponseEntity.ok(EquipementFicheResponse.of(
                new EquipementResponse(equipement), nbInterventions, heuresIndispo, coutPeriode, coutCumule,
                analyse, aideDecision.seuilRatio(), derniere, prochainPlan));
    }

    /**
     * Vérification de disponibilité avant d'affecter un patient à ce générateur — avertissement non
     * bloquant (voir le wizard patient / l'affichage séance, qui l'utilisent pour un simple rappel).
     */
    @GetMapping("/disponibilite-patient")
    public ResponseEntity<DisponibilitePatientResponse> disponibilitePatient(
            @PathVariable String id, Authentication authentication) {
        Equipement equipement = requireEquipement(id, authentication);

        // La disponibilité ne dépend que de l'état de l'équipement : celui-ci est saisi par l'utilisateur au
        // démarrage et à la clôture de chaque intervention (un équipement « En service » reste affectable).
        boolean disponible = !STATUTS_INDISPONIBLES_PATIENT.contains(equipement.getStatut());

        return ResponseEntity.ok(new DisponibilitePatientResponse(disponible, equipement.getStatut()));
    }

    private Equipement requireEquipement(String id, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UUID centreId = UUID.fromString(principal.getCenterId());
        return equipementRepository.findById(UUID.fromString(id))
                .filter(e -> e.getCentreId().equals(centreId))
                .orElseThrow(() -> new IllegalArgumentException("Équipement non trouvé"));
    }
}
