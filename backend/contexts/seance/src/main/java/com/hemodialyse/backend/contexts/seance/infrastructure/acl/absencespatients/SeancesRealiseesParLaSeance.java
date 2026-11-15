package com.hemodialyse.backend.contexts.seance.infrastructure.acl.absencespatients;

import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.SeancesRealiseesFournies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesPubliees;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fournisseur des Absences de patients (ADR 0098, SE7d) : les séances réalisées passent par {@link SeancesRealiseesPubliees}. Logé dans la
 * Séance (ADR 0192 : la Séance dépend des Absences, jamais l'inverse). Délégation pure, lecture seule, ordre et doublons conservés.
 */
public class SeancesRealiseesParLaSeance implements SeancesRealiseesFournies {

    private final SeancesRealiseesPubliees seances;

    public SeancesRealiseesParLaSeance(SeancesRealiseesPubliees seances) {
        this.seances = seances;
    }

    @Override
    public List<JourRealiseFourni> joursRealisesDuCentre(UUID centre, LocalDate du, LocalDate au) {
        return seances.joursRealisesDuCentre(centre, du, au).stream().map(j -> new JourRealiseFourni(j.patient(), j.jour())).toList();
    }

    @Override
    public List<LocalDate> joursRealisesDuPatient(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        return seances.joursRealisesDuPatient(centre, patient, du, au);
    }

    @Override
    public boolean estRealisee(UUID centre, UUID patient, LocalDate jour) {
        return seances.estRealisee(centre, patient, jour);
    }

    @Override
    public List<UUID> patientsRealisesLe(UUID centre, LocalDate jour) {
        return seances.patientsRealisesLe(centre, jour);
    }
}
