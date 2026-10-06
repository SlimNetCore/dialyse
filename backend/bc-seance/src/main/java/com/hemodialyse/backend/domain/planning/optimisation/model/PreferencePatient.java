package com.hemodialyse.backend.domain.planning.optimisation.model;

import java.util.UUID;

/**
 * Préférences de planification d'un patient. {@code creneauPrefereId} : créneau souhaité (transport, travail…), pris en
 * compte autant que possible. {@code joursAChoisir} : ses jours de dialyse sont choisis par l'optimisation (nouveau
 * patient, ou jours à revoir) parmi des schémas bien espacés de {@code seancesParSemaine} séances ; sinon ses jours
 * prescrits ne changent jamais.
 */
public record PreferencePatient(UUID patientId, UUID creneauPrefereId, Integer seancesParSemaine,
                                boolean joursAChoisir) {

    public static final int SEANCES_MAX = 7;

    public PreferencePatient {
        if (patientId == null) throw new IllegalArgumentException("Patient requis");
        if (seancesParSemaine != null && (seancesParSemaine < 1 || seancesParSemaine > SEANCES_MAX)) {
            throw new IllegalArgumentException("Le nombre de séances par semaine doit être compris entre 1 et " + SEANCES_MAX);
        }
        if (joursAChoisir && seancesParSemaine == null) {
            throw new IllegalArgumentException("Le nombre de séances par semaine est requis pour choisir les jours");
        }
    }

    public static PreferencePatient aucune(UUID patientId) {
        return new PreferencePatient(patientId, null, null, false);
    }
}
