package com.hemodialyse.backend.application.gmao;

import com.hemodialyse.backend.application.planning.GenerateurIndisponibleService;
import com.hemodialyse.backend.domain.gmao.model.Equipement;
import com.hemodialyse.backend.domain.gmao.model.EquipementStatutHistorique;
import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;
import com.hemodialyse.backend.domain.gmao.port.EquipementRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.EquipementStatutHistoriqueRepositoryPort;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Répercute sur l'équipement l'état saisi lors d'une intervention (au démarrage et à la clôture), avec
 * journal de statut (indisponibilité) et éviction du cache référentiel des générateurs : le wizard patient
 * doit voir immédiatement le nouvel état. Isolation multi-centre : l'équipement doit appartenir au centre.
 */
@Service
public class InterventionEquipementStatutService {

    private final EquipementRepositoryPort equipementRepository;
    private final EquipementStatutHistoriqueRepositoryPort historiqueRepository;
    private final GenerateurIndisponibleService generateurIndisponible;

    public InterventionEquipementStatutService(
            EquipementRepositoryPort equipementRepository,
            EquipementStatutHistoriqueRepositoryPort historiqueRepository,
            GenerateurIndisponibleService generateurIndisponible) {
        this.equipementRepository = equipementRepository;
        this.historiqueRepository = historiqueRepository;
        this.generateurIndisponible = generateurIndisponible;
    }

    /**
     * @throws IllegalArgumentException si l'équipement n'existe pas dans ce centre
     */
    public Equipement requireEquipement(UUID equipementId, UUID centreId) {
        return equipementRepository.findById(equipementId)
                .filter(e -> e.getCentreId().equals(centreId))
                .orElseThrow(() -> new IllegalArgumentException("Équipement non trouvé"));
    }

    /**
     * Applique l'état constaté ; sans effet (aucun historique) si l'équipement est déjà dans cet état
     * ou si l'état n'est pas renseigné (interventions antérieures à cette règle).
     */
    @CacheEvict(cacheNames = "ref.generateurs", allEntries = true)
    public void appliquerEtat(UUID equipementId, UUID centreId, StatutEquipement etat, String motif, UUID parUtilisateur) {
        Equipement equipement = requireEquipement(equipementId, centreId);
        if (etat == null) return;
        StatutEquipement precedent = equipement.getStatut();
        if (!equipement.changerStatutIntervention(etat, motif, parUtilisateur)) return;
        equipementRepository.save(equipement);
        historiqueRepository.save(EquipementStatutHistorique.enregistrer(
                equipement.getId(), equipement.getCentreId(), precedent, equipement.getStatut(), motif, parUtilisateur));
        generateurIndisponible.signaler(equipement, precedent);
    }
}
