package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.SemaineDuPlanning;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Suivi de la semaine du planning (dimanche au samedi) qui contient le jour demandé, ou le jour courant sans jour demandé.
 */
public class SuivreLaSemaine {

    private final LectureDesAbsences absences;
    private final SeancesRealisees seances;
    private final Clock horloge;

    public SuivreLaSemaine(LectureDesAbsences absences, SeancesRealisees seances, Clock horloge) {
        this.absences = absences;
        this.seances = seances;
        this.horloge = horloge;
    }

    /**
     * @param jour un jour de la semaine voulue ; {@code null} = aujourd'hui
     */
    public SuiviDeLaSemaine executer(CentreId centre, LocalDate jour) {
        SemaineDuPlanning semaine = SemaineDuPlanning.contenant(jour != null ? jour : LocalDate.now(horloge));
        return new SuiviDeLaSemaine(absences.absencesNonAnnuleesEntre(centre, semaine.dimanche(), semaine.samedi()),
                seances.duCentre(centre, semaine.dimanche(), semaine.samedi()));
    }
}
