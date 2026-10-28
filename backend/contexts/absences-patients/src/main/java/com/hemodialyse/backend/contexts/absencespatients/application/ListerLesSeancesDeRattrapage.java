package com.hemodialyse.backend.contexts.absencespatients.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceIntrouvable;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceNonModifiable;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceSuivie;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.SeancesDeRattrapage;

import java.time.LocalDate;
import java.util.List;

/**
 * Dates des séances réalisées qui peuvent rattraper une absence (RG-ABS-021) : après la séance manquée, au plus tard
 * aujourd'hui, pas déjà utilisées pour une autre absence du patient. N'est proposé que pour une absence encore
 * rattrapable (KB-ABS-04) ; une absence inconnue ou d'un autre centre est introuvable (KB-ABS-03).
 */
public class ListerLesSeancesDeRattrapage {

    private final LectureDesAbsences absences;
    private final SeancesRealisees seances;
    private final HorlogeDesCentres horloge;

    public ListerLesSeancesDeRattrapage(LectureDesAbsences absences, SeancesRealisees seances, HorlogeDesCentres horloge) {
        this.absences = absences;
        this.seances = seances;
        this.horloge = horloge;
    }

    /**
     * @throws AbsenceIntrouvable   absence inconnue de ce centre
     * @throws AbsenceNonModifiable absence rattrapée ou annulée
     */
    public List<LocalDate> executer(CentreId centre, AbsenceId id) {
        AbsenceSuivie absence = absences.absence(centre, id).orElseThrow(() -> new AbsenceIntrouvable(id));
        SeancesDeRattrapage.exigerRattrapable(absence);
        List<LocalDate> utilisees = absences.datesDeRattrapageUtilisees(centre, absence.patient());
        List<LocalDate> realisees = seances.joursDuPatient(centre, absence.patient(),
                SeancesDeRattrapage.premierJourPossible(absence), horloge.aujourdhui(centre));
        return SeancesDeRattrapage.proposees(realisees, utilisees);
    }
}
