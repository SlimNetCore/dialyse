package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.absencespatients.application.SeancesRealisees;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.SeanceRealisee;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.SeancesRealiseesFournies;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;

/**
 * ACL des Absences de patients vers la Séance (ADR 0098, SE7d) : les séances réalisées viennent de {@link SeancesRealiseesFournies}
 * (implémenté par la plateforme sur {@code seance.v1}). Traduction seule : identifiants typés, ordre et lignes inchangés (dates croissantes,
 * ordre intra-jour non défini, KB-SEA-77). Remplace {@code legacyseance/SeancesRealiseesJdbc}.
 */
public class SeancesRealiseesPubliees implements SeancesRealisees {

    private final SeancesRealiseesFournies seances;

    public SeancesRealiseesPubliees(SeancesRealiseesFournies seances) {
        this.seances = seances;
    }

    @Override
    public List<SeanceRealisee> duCentre(CentreId centre, LocalDate du, LocalDate au) {
        return seances.joursRealisesDuCentre(centre.valeur(), du, au).stream()
                .map(j -> new SeanceRealisee(new PatientId(j.patient()), j.jour())).toList();
    }

    @Override
    public List<LocalDate> joursDuPatient(CentreId centre, PatientId patient, LocalDate du, LocalDate au) {
        return seances.joursRealisesDuPatient(centre.valeur(), patient.valeur(), du, au);
    }

    @Override
    public boolean realisee(CentreId centre, PatientId patient, LocalDate jour) {
        return seances.estRealisee(centre.valeur(), patient.valeur(), jour);
    }
}
