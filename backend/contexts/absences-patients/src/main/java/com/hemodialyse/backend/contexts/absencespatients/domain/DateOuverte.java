package com.hemodialyse.backend.contexts.absencespatients.domain;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

/**
 * Preuve qu'on peut encore écrire une absence à cette date : la période du patient n'est pas facturée (RG-ABS-024). Exigée
 * par chaque écriture ; la détection de nuit, elle, ignore sans erreur une date qui ne l'est pas (période facturée ou mois
 * d'absences clos). Le mois d'absences clos refuse les écritures des utilisateurs par {@code GardeDesMoisDAbsences} (couche
 * application : il faut verrouiller la ligne du mois dans la transaction).
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

    /**
     * Version de la détection : vide (on ignore) si la période du patient est facturée ou si le mois d'absences est clos. À appeler
     * dans la transaction de l'écriture : la ligne du mois est lue sous verrou (une clôture concurrente se sérialise).
     */
    public static Optional<DateOuverte> siOuverte(PeriodesFacturees periodes, MoisDAbsencesOuverts mois, CentreId centre,
                                                  PatientId patient, LocalDate jour) {
        if (!mois.estOuvertSousVerrou(centre, YearMonth.from(jour)) || periodes.estFacturee(centre, patient, jour)) {
            return Optional.empty();
        }
        return Optional.of(new DateOuverte(centre, patient, jour));
    }
}
