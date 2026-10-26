package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.ParametrageDeLaPlanification;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.PlanningParametres;
import com.hemodialyse.backend.domain.planning.port.PlanningParametresPort;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * ACL du legacy vers le paramétrage du planning de la Planification (slice P1, ADR 0053) : le port de lecture historique (jours,
 * salles d'isolement, ratio, patients par poste) est servi par le langage publié du contexte, qui tient désormais ces données.
 * Traduction sans logique : jours civils vers {@link JourSemaine}. Retirée avec ses consommateurs (présence, optimisation).
 */
@Component
public class PlanningParametresDuContexte implements PlanningParametresPort {

    private final ParametrageDeLaPlanification parametrage;

    public PlanningParametresDuContexte(ParametrageDeLaPlanification parametrage) {
        this.parametrage = parametrage;
    }

    @Override
    public PlanningParametres lire(UUID centerId) {
        ParametrageDeLaPlanification.Parametres p = parametrage.parametres(centerId);
        Set<JourSemaine> jours = EnumSet.noneOf(JourSemaine.class);
        p.joursOuverts().forEach(jour -> jours.add(JourSemaine.de(jour)));
        return new PlanningParametres(jours, p.sallesIsolement(), p.patientsParInfirmier(), p.patientsParPosteEtSerie());
    }
}
