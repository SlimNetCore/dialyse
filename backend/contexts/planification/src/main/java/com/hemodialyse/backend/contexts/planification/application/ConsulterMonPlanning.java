package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.SemaineDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SemaineDeLInfirmier.PlanningDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;

import java.time.LocalDate;

/**
 * « Mon planning » : la fiche liée au compte connecté, avec son roulement, et sa semaine dans la grille du centre (RG-INF-031).
 * Sans date, la semaine courante du centre (jour du centre, pas UTC, KB-PLN-32). Une fiche désactivée reste lisible.
 */
public class ConsulterMonPlanning {
    private final InfirmiersRepository infirmiers;
    private final FichesDetaillees fiches;
    private final SemaineDeLInfirmier semaines;
    private final HorlogeDesCentres horloge;

    public ConsulterMonPlanning(InfirmiersRepository infirmiers, FichesDetaillees fiches, SemaineDeLInfirmier semaines,
                                HorlogeDesCentres horloge) {
        this.infirmiers = infirmiers;
        this.fiches = fiches;
        this.semaines = semaines;
        this.horloge = horloge;
    }

    public record MonPlanning(FicheDetaillee infirmier, PlanningDeLaSemaine semaine) {
    }

    /**
     * @param date un jour de la semaine voulue, ou {@code null} pour la semaine courante
     */
    public MonPlanning executer(CentreId centre, CompteId compte, LocalDate date) {
        Infirmier fiche = FicheLiee.de(infirmiers, centre, compte);
        LocalDate jour = date != null ? date : horloge.aujourdhui(centre);
        return new MonPlanning(fiches.de(fiche), semaines.semaine(centre.valeur(), fiche.id().valeur(), jour));
    }
}
