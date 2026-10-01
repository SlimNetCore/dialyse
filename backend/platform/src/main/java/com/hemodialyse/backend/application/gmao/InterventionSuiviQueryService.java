package com.hemodialyse.backend.application.gmao;

import com.hemodialyse.backend.domain.gmao.model.EvenementIntervention;
import com.hemodialyse.backend.domain.gmao.model.Intervention;
import com.hemodialyse.backend.domain.gmao.port.EquipementRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.EquipementStatutHistoriqueRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.InterventionRepositoryPort;
import com.hemodialyse.backend.domain.gmao.service.IndicateursIntervention;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lecture du suivi d'une intervention : indicateurs de réactivité et ligne de temps (qui a fait quoi, quand).
 * Toujours bornée au centre de l'appelant (AGENTS.md §2).
 */
@Service
public class InterventionSuiviQueryService {

    private final InterventionRepositoryPort interventionRepository;
    private final EquipementRepositoryPort equipementRepository;
    private final EquipementStatutHistoriqueRepositoryPort historiqueRepository;
    private final JdbcTemplate jdbc;

    public InterventionSuiviQueryService(
            InterventionRepositoryPort interventionRepository,
            EquipementRepositoryPort equipementRepository,
            EquipementStatutHistoriqueRepositoryPort historiqueRepository,
            JdbcTemplate jdbc) {
        this.interventionRepository = interventionRepository;
        this.equipementRepository = equipementRepository;
        this.historiqueRepository = historiqueRepository;
        this.jdbc = jdbc;
    }

    public IndicateursIntervention.Resultat indicateurs(UUID interventionId, UUID centreId) {
        Intervention intervention = requireIntervention(interventionId, centreId);
        var equipement = equipementRepository.findById(intervention.getEquipementId())
                .filter(e -> e.getCentreId().equals(centreId))
                .orElseThrow(() -> new IllegalArgumentException("Équipement non trouvé"));
        return IndicateursIntervention.calculer(
                intervention,
                historiqueRepository.findByEquipementIdOrderByChangedAtAsc(equipement.getId()),
                equipement.getStatut(),
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    public List<Evenement> chronologie(UUID interventionId, UUID centreId) {
        Intervention intervention = requireIntervention(interventionId, centreId);
        Map<UUID, String> noms = new HashMap<>();
        return intervention.chronologie().stream()
                .map(e -> new Evenement(e, e.par() == null ? null
                        : noms.computeIfAbsent(e.par(), this::nomUtilisateur)))
                .toList();
    }

    private Intervention requireIntervention(UUID id, UUID centreId) {
        return interventionRepository.findById(id)
                .filter(i -> i.getCentreId().equals(centreId))
                .orElseThrow(() -> new IllegalArgumentException("Intervention non trouvée"));
    }

    private String nomUtilisateur(UUID userId) {
        List<String> noms = jdbc.queryForList(
                "SELECT COALESCE(full_name, username) FROM app_user WHERE id = ?", String.class, userId);
        return noms.isEmpty() ? null : noms.get(0);
    }

    public record Evenement(EvenementIntervention evenement, String parNom) {
    }
}
