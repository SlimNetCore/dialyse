package com.hemodialyse.backend.application.infirmier;

import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.port.AffectationInfirmierRepositoryPort;
import com.hemodialyse.backend.domain.infirmier.port.InfirmierRepositoryPort;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.port.PlanningDonneesPort;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Roulement des infirmiers : salle, créneau et jours de travail. La salle et le créneau doivent appartenir au centre
 * et un infirmier ne peut pas être affecté à deux salles sur le même créneau le même jour.
 */
@Service
public class AffectationInfirmierService {

    private final AffectationInfirmierRepositoryPort affectations;
    private final InfirmierRepositoryPort infirmiers;
    private final PlanningDonneesPort planning;

    public AffectationInfirmierService(AffectationInfirmierRepositoryPort affectations,
                                       InfirmierRepositoryPort infirmiers, PlanningDonneesPort planning) {
        this.affectations = affectations;
        this.infirmiers = infirmiers;
        this.planning = planning;
    }

    public AffectationInfirmier ajouter(UUID centerId, UUID infirmierId, UUID salleId, UUID creneauId,
                                        Set<JourSemaine> jours) {
        infirmiers.findById(centerId, infirmierId)
                .orElseThrow(() -> new BusinessException("INFIRMIER_INTROUVABLE", "Infirmier introuvable"));
        verifierSalleEtCreneau(centerId, salleId, creneauId);
        AffectationInfirmier nouvelle = AffectationInfirmier.creer(centerId, infirmierId, salleId, creneauId, jours);
        verifierSansChevauchement(centerId, nouvelle);
        return affectations.save(nouvelle);
    }

    public AffectationInfirmier modifier(UUID centerId, UUID infirmierId, UUID affectationId, UUID salleId,
                                         UUID creneauId, Set<JourSemaine> jours) {
        AffectationInfirmier modifiee = charger(centerId, infirmierId, affectationId).modifier(salleId, creneauId, jours);
        verifierSalleEtCreneau(centerId, salleId, creneauId);
        verifierSansChevauchement(centerId, modifiee);
        return affectations.save(modifiee);
    }

    public void supprimer(UUID centerId, UUID infirmierId, UUID affectationId) {
        charger(centerId, infirmierId, affectationId);
        affectations.delete(centerId, affectationId);
    }

    private AffectationInfirmier charger(UUID centerId, UUID infirmierId, UUID affectationId) {
        return affectations.findById(centerId, affectationId)
                .filter(a -> a.infirmierId().equals(infirmierId))
                .orElseThrow(() -> new BusinessException("AFFECTATION_INTROUVABLE", "Affectation introuvable"));
    }

    private void verifierSalleEtCreneau(UUID centerId, UUID salleId, UUID creneauId) {
        if (!planning.sallesDuCentre(centerId).contains(salleId)) {
            throw new BusinessException("AFFECTATION_SALLE_INCONNUE", "Cette salle n'appartient pas au centre");
        }
        if (!planning.creneauxDuCentre(centerId).contains(creneauId)) {
            throw new BusinessException("AFFECTATION_CRENEAU_INCONNU", "Ce créneau n'appartient pas au centre");
        }
    }

    private void verifierSansChevauchement(UUID centerId, AffectationInfirmier candidate) {
        List<AffectationInfirmier> existantes = affectations.findByInfirmierIds(centerId, List.of(candidate.infirmierId()));
        if (existantes.stream().anyMatch(candidate::chevauche)) {
            throw new BusinessException("AFFECTATION_CHEVAUCHEMENT",
                    "L'infirmier est déjà affecté à ce créneau l'un de ces jours");
        }
    }
}
