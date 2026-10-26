package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;

/**
 * Préférence de planification d'un patient (RG-PLN-093) : créneau souhaité (transport, travail…), pris en compte autant que
 * possible ; {@code joursAChoisir} : ses jours de dialyse sont choisis par l'optimisation parmi des schémas bien espacés de
 * {@code seancesParSemaine} séances (1 à 7) ; sinon ses jours prescrits ne changent jamais. Sans préférence, le patient n'a
 * ni créneau préféré ni jours à choisir.
 */
public record PreferenceDePlanification(PatientId patient, CreneauId creneauPrefere, Integer seancesParSemaine,
                                        boolean joursAChoisir) {

    public static final int SEANCES_MAX = 7;

    public PreferenceDePlanification {
        if (patient == null) throw new SaisieInvalide("Patient requis");
        if (seancesParSemaine != null && (seancesParSemaine < 1 || seancesParSemaine > SEANCES_MAX)) {
            throw new SaisieInvalide("Le nombre de séances par semaine doit être compris entre 1 et " + SEANCES_MAX);
        }
        if (joursAChoisir && seancesParSemaine == null) {
            throw new SaisieInvalide("Le nombre de séances par semaine est requis pour choisir les jours");
        }
    }

    public static PreferenceDePlanification aucune(PatientId patient) {
        return new PreferenceDePlanification(patient, null, null, false);
    }
}
