package com.hemodialyse.backend.application.planning;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.Alerte;
import com.hemodialyse.backend.domain.planning.model.Planning.CaseGrille;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.DemandePlacement;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.Proposition;
import com.hemodialyse.backend.domain.planning.model.Planning.ResultatProposition;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.planning.port.PlanningDonneesPort;
import com.hemodialyse.backend.domain.planning.service.PlanificationAffectationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Aide au placement d'un patient : propose rapidement les salles ayant une place, les créneaux et les générateurs
 * libres, en respectant les jours d'ouverture du centre et l'isolement des patients à risque infectieux, et fournit la
 * grille de disponibilité du centre. Lecture seule, bornée au centre. Aucune mise en cache : les placements changent à
 * chaque fiche patient enregistrée et la réponse doit refléter l'instant.
 */
@Service
public class PlanningAffectationQueryService {

    private final PlanningDonneesPort donnees;

    public PlanningAffectationQueryService(PlanningDonneesPort donnees) {
        this.donnees = donnees;
    }

    /**
     * @param patientAIgnorer patient dont on modifie le placement (son placement actuel est libéré) ; peut être null
     * @param isolement       null : risque déduit des sérologies du patient (sans risque si le patient est inconnu) ;
     *                        sinon valeur forcée par l'utilisateur
     */
    public Resultat proposer(UUID centerId, int seances, Set<JourSemaine> jours, UUID creneau, UUID salle,
                             int limite, UUID patientAIgnorer, Boolean isolement) {
        boolean aRisque = isolement != null ? isolement
                : patientAIgnorer != null && donnees.patientARisque(centerId, patientAIgnorer);
        DemandePlacement demande = new DemandePlacement(seances, jours == null ? Set.of() : jours, creneau, salle, aRisque);
        DonneesPlanning d = donnees.charger(centerId, patientAIgnorer);
        ResultatProposition r = PlanificationAffectationService.rechercher(d, demande, limite);
        return new Resultat(d.salles(), d.creneaux(), r.propositions(), PlanificationAffectationService.grille(d),
                r.alertes(), aRisque);
    }

    public record Resultat(List<SalleRef> salles, List<CreneauRef> creneaux, List<Proposition> propositions,
                           List<CaseGrille> grille, List<Alerte> alertes, boolean patientARisque) {
    }
}
