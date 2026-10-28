package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.Donnees;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.Semaine;

import java.time.LocalDate;

/**
 * Le planning réel d'une semaine du centre (RG-PLN-040 à 045). Lecture seule, bornée au centre, jamais mise en cache. Sans date, la
 * semaine est celle d'aujourd'hui au fuseau du centre (KB-PLN-69).
 */
public class ConsulterLaSemaine {

    private final LectureDesRessources ressources;
    private final SejoursDesPatients patients;
    private final SeancesRealisees seances;
    private final DeplacementsTemporaires deplacements;
    private final HorlogeDesCentres horloge;

    public ConsulterLaSemaine(LectureDesRessources ressources, SejoursDesPatients patients, SeancesRealisees seances,
                              DeplacementsTemporaires deplacements, HorlogeDesCentres horloge) {
        this.ressources = ressources;
        this.patients = patients;
        this.seances = seances;
        this.deplacements = deplacements;
        this.horloge = horloge;
    }

    /** @param jour n'importe quel jour de la semaine voulue ; nul : aujourd'hui, jour du centre */
    public Semaine executer(CentreId centre, LocalDate jour) {
        LocalDate debut = SemaineDuPlanning.debutDeLaSemaineDe(jour != null ? jour : horloge.aujourdhui(centre));
        LocalDate fin = debut.plusDays(6);
        return SemaineDuPlanning.construire(new Donnees(ressources.duCentre(centre, debut), patients.duCentre(centre),
                seances.duCentre(centre, debut, fin), deplacements.duCentre(centre, debut, fin)), debut);
    }
}
