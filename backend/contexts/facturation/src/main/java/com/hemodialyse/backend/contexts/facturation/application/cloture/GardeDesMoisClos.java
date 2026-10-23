package com.hemodialyse.backend.contexts.facturation.application.cloture;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationRepository;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PeriodeDeFacturation;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Garde de la clôture mensuelle (RG-FAC-041), appelée par l'émission et par les modifications de la simulation : refuse
 * {@code PERIODE_DE_FACTURATION_CLOTUREE} si un mois visé est clos. À appeler <b>dans la transaction</b> du cas d'usage : la ligne
 * de chaque mois visé est verrouillée jusqu'au commit, donc une clôture concurrente attend la fin de l'émission (ou l'inverse) et
 * un mois clos ne reçoit jamais de facture émise après sa clôture. Les mois se verrouillent toujours du plus ancien au plus
 * récent, pour qu'aucun interblocage ne soit possible entre deux émissions.
 */
public final class GardeDesMoisClos {

    private final MoisDeFacturationRepository mois;

    public GardeDesMoisClos(MoisDeFacturationRepository mois) {
        this.mois = mois;
    }

    /**
     * Tout mois couvert par l'intervalle doit être ouvert. Les mois sont verrouillés ensemble, du plus ancien au plus récent ; le
     * premier mois clos est refusé.
     */
    public void exigerOuverts(CentreId centre, PeriodeDeFacturation periode) {
        mois.verrouillerDe(centre, YearMonth.from(periode.debut()), YearMonth.from(periode.fin()))
                .forEach(MoisDeFacturation::exigerOuvert);
    }

    /** Le mois de la date (celle d'une séance) doit être ouvert. */
    public void exigerOuvert(CentreId centre, LocalDate date) {
        mois.verrouiller(centre, YearMonth.from(date)).exigerOuvert();
    }
}
