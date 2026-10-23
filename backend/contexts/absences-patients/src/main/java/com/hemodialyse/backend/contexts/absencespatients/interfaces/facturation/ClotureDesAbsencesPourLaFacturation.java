package com.hemodialyse.backend.contexts.absencespatients.interfaces.facturation;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisDAbsencesOuverts;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ClotureDesAbsences;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Adapter entrant fourni à la Facturation : le langage publié {@link ClotureDesAbsences} (ADR 0042). Lecture seule de l'état du
 * mois d'absences, sans verrou : la Facturation n'en tire qu'un avertissement.
 */
public class ClotureDesAbsencesPourLaFacturation implements ClotureDesAbsences {

    private final MoisDAbsencesOuverts mois;

    public ClotureDesAbsencesPourLaFacturation(MoisDAbsencesOuverts mois) {
        this.mois = mois;
    }

    @Override
    public boolean moisClos(UUID centre, YearMonth moisDemande) {
        return !mois.estOuvert(new CentreId(centre), moisDemande);
    }
}
