package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.ClassementDesRemplacants;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Candidat;
import com.hemodialyse.backend.contexts.planification.domain.PresenceDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Remplacement;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CalendrierDuCentre;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlanningDesPlaces;

import java.time.LocalDate;
import java.util.List;

/**
 * Propose au plus {@value #LIMITE} remplaçants classés pour une case (RG-INF-051). La case est VALIDÉE comme pour l'affectation d'un
 * remplaçant (décision du 27/10/2026, KB-PLN-48) : salle et créneau du centre (422), date non passée au jour du centre, jour ouvert
 * (422). Lecture seule.
 */
public class ClasserLesRemplacants {
    public static final int LIMITE = 10;

    private final LecturesDeLaPresence lectures;
    private final SallesDuCentre salles;
    private final CreneauxDuCentre creneaux;
    private final CalendrierDuCentre calendrier;
    private final HorlogeDesCentres horloge;

    public ClasserLesRemplacants(PersonnelDeLaPresence personnel, PlanningDesPlaces places, SallesDuCentre salles,
                                 CreneauxDuCentre creneaux, CalendrierDuCentre calendrier, HorlogeDesCentres horloge) {
        this.lectures = new LecturesDeLaPresence(personnel, places);
        this.salles = salles;
        this.creneaux = creneaux;
        this.calendrier = calendrier;
        this.horloge = horloge;
    }

    public List<Candidat> executer(CentreId centre, LocalDate date, SalleId salle, CreneauId creneau) {
        CaseDuCentre.exiger(salles, creneaux, centre, salle, creneau);
        Remplacement.exigerUneDateDeTravail(date, horloge.aujourdhui(centre), calendrier.ouvert(centre.valeur(), date));
        LocalDate debut = PresenceDeLaSemaine.debutDeSemaine(date);
        return ClassementDesRemplacants.proposer(lectures.charger(centre, debut, debut.plusDays(6)), date, salle.valeur(),
                creneau.valeur(), LIMITE);
    }
}
