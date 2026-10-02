package com.hemodialyse.backend.application.infirmier;

import com.hemodialyse.backend.domain.infirmier.model.Presence.Candidat;
import com.hemodialyse.backend.domain.infirmier.model.RemplacementInfirmier;
import com.hemodialyse.backend.domain.infirmier.port.InfirmierRepositoryPort;
import com.hemodialyse.backend.domain.infirmier.port.RemplacementInfirmierRepositoryPort;
import com.hemodialyse.backend.domain.infirmier.service.RemplacantService;
import com.hemodialyse.backend.domain.planning.port.PlanningDonneesPort;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Affectation ponctuelle d'un remplaçant sur une case (date, salle, créneau). Le remplaçant doit figurer parmi les
 * candidats éligibles : ni absent, ni déjà prévu sur le créneau, habilité si la salle est une salle d'isolement.
 */
@Service
public class RemplacementInfirmierService {

    private final RemplacementInfirmierRepositoryPort remplacements;
    private final InfirmierRepositoryPort infirmiers;
    private final PlanningDonneesPort planning;
    private final PresenceInfirmierQueryService presence;

    public RemplacementInfirmierService(RemplacementInfirmierRepositoryPort remplacements,
                                        InfirmierRepositoryPort infirmiers, PlanningDonneesPort planning,
                                        PresenceInfirmierQueryService presence) {
        this.remplacements = remplacements;
        this.infirmiers = infirmiers;
        this.planning = planning;
        this.presence = presence;
    }

    public RemplacementInfirmier affecter(UUID centerId, LocalDate date, UUID salleId, UUID creneauId, UUID infirmierId,
                                          UUID remplaceId) {
        if (!planning.sallesDuCentre(centerId).contains(salleId)) {
            throw new BusinessException("AFFECTATION_SALLE_INCONNUE", "Cette salle n'appartient pas au centre");
        }
        if (!planning.creneauxDuCentre(centerId).contains(creneauId)) {
            throw new BusinessException("AFFECTATION_CRENEAU_INCONNU", "Ce créneau n'appartient pas au centre");
        }
        if (remplaceId != null && infirmiers.findById(centerId, remplaceId).isEmpty()) {
            throw new BusinessException("INFIRMIER_INTROUVABLE", "Infirmier introuvable");
        }
        boolean eligible = RemplacantService.proposer(presence.donneesSemaine(centerId, date), date, salleId, creneauId,
                Integer.MAX_VALUE).stream().map(Candidat::infirmierId).anyMatch(infirmierId::equals);
        if (!eligible) {
            throw new BusinessException("REMPLACEMENT_INELIGIBLE",
                    "Cet infirmier ne peut pas être affecté à cette case (absent, déjà prévu ou non habilité)");
        }
        return remplacements.save(RemplacementInfirmier.creer(centerId, date, salleId, creneauId, infirmierId, remplaceId));
    }

    public void annuler(UUID centerId, UUID id) {
        remplacements.findById(centerId, id)
                .orElseThrow(() -> new BusinessException("REMPLACEMENT_INTROUVABLE", "Remplacement introuvable"));
        remplacements.delete(centerId, id);
    }
}
