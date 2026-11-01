package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.ObjectifDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Perimetre;
import com.hemodialyse.backend.contexts.planification.domain.SaisieInvalide;

import java.time.LocalDate;

/**
 * @param debutSemaine           un jour de la première semaine (défaut : semaine en cours au jour du centre)
 * @param nbSemaines             horizon en semaines (défaut 1)
 * @param dureeMaxSecondes       durée maximale de calcul par phase (défaut 20)
 * @param stabilite              0 à 10 (défaut 5)
 * @param objectif               {@code EQUITE} (défaut) ou {@code ECONOMIE}
 * @param maxVacationsParJour    1 à 3 (défaut 2)
 * @param maxVacationsParSemaine 1 à 14 (défaut 6)
 */
public record LancerDemande(Perimetre perimetre, LocalDate debutSemaine, Integer nbSemaines,
                            Integer dureeMaxSecondes,
                            Integer stabilite, ObjectifDesInfirmiers objectif, Integer maxVacationsParJour,
                            Integer maxVacationsParSemaine) {

    public ParametresDOptimisation versParametres(LocalDate aujourdhui) {
        ParametresDOptimisation defauts = ParametresDOptimisation.parDefaut(
                perimetre == null ? Perimetre.PATIENTS : perimetre, debutSemaine != null ? debutSemaine : aujourdhui);
        if (perimetre == null) {
            throw new SaisieInvalide("Périmètre requis");
        }
        return new ParametresDOptimisation(perimetre, defauts.debutSemaine(),
                nbSemaines != null ? nbSemaines : defauts.nbSemaines(),
                dureeMaxSecondes != null ? dureeMaxSecondes : defauts.dureeMaxSecondes(),
                stabilite != null ? stabilite : defauts.stabilite(),
                objectif != null ? objectif : defauts.objectif(),
                maxVacationsParJour != null ? maxVacationsParJour : defauts.maxVacationsParJour(),
                maxVacationsParSemaine != null ? maxVacationsParSemaine : defauts.maxVacationsParSemaine());
    }
}
