package com.hemodialyse.backend.application.planning;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.PlanningParametres;
import com.hemodialyse.backend.domain.planning.port.PlanningDonneesPort;
import com.hemodialyse.backend.domain.planning.port.PlanningParametresPort;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Lecture et mise à jour du paramétrage du planning d'un centre : jours d'ouverture et salles d'isolement. Les salles
 * d'isolement doivent appartenir au centre.
 */
@Service
public class PlanningParametresService {

    private final PlanningParametresPort parametres;
    private final PlanningDonneesPort donnees;

    public PlanningParametresService(PlanningParametresPort parametres, PlanningDonneesPort donnees) {
        this.parametres = parametres;
        this.donnees = donnees;
    }

    public PlanningParametres lire(UUID centerId) {
        return parametres.lire(centerId);
    }

    /**
     * @throws IllegalArgumentException aucun jour d'ouverture, ou salle d'isolement étrangère au centre
     */
    public PlanningParametres enregistrer(UUID centerId, Set<JourSemaine> joursOuverts, Set<UUID> sallesIsolement) {
        PlanningParametres nouveaux = new PlanningParametres(joursOuverts, sallesIsolement);
        if (!donnees.sallesDuCentre(centerId).containsAll(nouveaux.sallesIsolement())) {
            throw new IllegalArgumentException("Une salle d'isolement n'appartient pas à ce centre");
        }
        parametres.enregistrer(centerId, nouveaux);
        return nouveaux;
    }
}
