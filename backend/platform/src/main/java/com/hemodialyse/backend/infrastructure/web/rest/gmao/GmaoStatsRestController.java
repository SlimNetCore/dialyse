package com.hemodialyse.backend.infrastructure.web.rest.gmao;

import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;
import com.hemodialyse.backend.domain.gmao.model.StatutIntervention;
import com.hemodialyse.backend.domain.gmao.port.EquipementRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.InterventionRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.PlanMaintenanceRepositoryPort;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.response.gmao.GmaoStatsResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Statistiques du dashboard GMAO, calculées côté serveur via des comptages — jamais en
 * rapatriant les listes complètes côté frontend (AGENTS.md §9).
 */
@RestController
@RequestMapping("/api/v1/gmao/stats")
@PreAuthorize("hasRole('ADMIN')")
public class GmaoStatsRestController {

    private final EquipementRepositoryPort equipementRepository;
    private final InterventionRepositoryPort interventionRepository;
    private final PlanMaintenanceRepositoryPort planRepository;

    public GmaoStatsRestController(
            EquipementRepositoryPort equipementRepository,
            InterventionRepositoryPort interventionRepository,
            PlanMaintenanceRepositoryPort planRepository) {
        this.equipementRepository = equipementRepository;
        this.interventionRepository = interventionRepository;
        this.planRepository = planRepository;
    }

    @GetMapping
    public GmaoStatsResponse statistiques(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UUID centreId = UUID.fromString(principal.getCenterId());

        return new GmaoStatsResponse(
                equipementRepository.countByCentreId(centreId),
                equipementRepository.countByCentreIdAndStatut(centreId, StatutEquipement.EN_SERVICE.name()),
                equipementRepository.countByCentreIdAndStatut(centreId, StatutEquipement.EN_MAINTENANCE.name()),
                equipementRepository.countByCentreIdAndStatut(centreId, StatutEquipement.HORS_SERVICE.name()),
                interventionRepository.countByCentreId(centreId),
                interventionRepository.countByCentreIdAndStatutEnCours(centreId),
                interventionRepository.countByCentreIdAndStatut(centreId, StatutIntervention.TERMINEE.name()),
                planRepository.countActiveByCentreId(centreId),
                planRepository.findOverdueByCentreId(centreId, OffsetDateTime.now(ZoneOffset.UTC)).size(),
                interventionRepository.countEnRetardByCentreId(centreId, OffsetDateTime.now(ZoneOffset.UTC))
        );
    }
}
