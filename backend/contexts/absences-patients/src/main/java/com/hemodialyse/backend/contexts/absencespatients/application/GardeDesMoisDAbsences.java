package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsencesRepository;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Garde de la clôture mensuelle des absences (A18) : refuse {@code MOIS_D_ABSENCES_CLOS} une écriture dont la date tombe dans un
 * mois clos. À appeler <b>dans la transaction</b> du cas d'usage : la ligne du mois est verrouillée jusqu'au commit, donc une
 * clôture concurrente attend la fin de l'écriture (ou l'inverse) et un mois clos ne reçoit jamais d'absence écrite après sa
 * clôture. Les mois se verrouillent toujours du plus ancien au plus récent (rattrapage : mois de l'absence puis mois de la
 * date de rattrapage), pour qu'aucun interblocage ne soit possible.
 */
public class GardeDesMoisDAbsences {

    private final MoisAbsencesRepository mois;

    public GardeDesMoisDAbsences(MoisAbsencesRepository mois) {
        this.mois = mois;
    }

    public void exigerOuvert(CentreId centre, YearMonth moisVise) {
        mois.verrouiller(centre, moisVise).exigerOuvert();
    }

    public void exigerOuvert(CentreId centre, LocalDate jour) {
        exigerOuvert(centre, YearMonth.from(jour));
    }
}
