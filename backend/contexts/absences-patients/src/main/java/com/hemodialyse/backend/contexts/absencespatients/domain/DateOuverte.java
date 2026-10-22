package com.hemodialyse.backend.contexts.absencespatients.domain;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;

import java.time.LocalDate;

/**
 * Preuve qu'on peut encore écrire une absence à cette date : la période du patient n'est pas facturée (RG-ABS-024). Exigée
 * par chaque écriture ; la clôture mensuelle des absences (A6) s'y ajoutera.
 */
public record DateOuverte(CentreId centre, PatientId patient, LocalDate jour) {

    /**
     * @throws RegleDAbsenceViolee {@code ABSENCE_PERIODE_FACTUREE}
     */
    public static DateOuverte exiger(PeriodesFacturees periodes, CentreId centre, PatientId patient, LocalDate jour) {
        if (periodes.estFacturee(centre, patient, jour)) {
            throw new RegleDAbsenceViolee("ABSENCE_PERIODE_FACTUREE",
                    "La facturation de cette période est validée : l'absence est clôturée");
        }
        return new DateOuverte(centre, patient, jour);
    }
}
