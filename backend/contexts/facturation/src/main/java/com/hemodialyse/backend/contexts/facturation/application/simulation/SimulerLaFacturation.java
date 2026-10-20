package com.hemodialyse.backend.contexts.facturation.application.simulation;

import com.hemodialyse.backend.contexts.facturation.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturationRepository;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.NumerotationPrevisionnelle;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PeriodeDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.Regroupement;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeancesFacturables;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SequenceDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.Simulation;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Cas d'usage « simuler la facturation d'une période » (RG-FAC-020 à 022) : l'aperçu des factures, sans rien enregistrer. La
 * facturation est datée du jour (numéros prévisionnels) ; le taux de TVA est celui en vigueur à la fin de la période facturée
 * (KB-FAC-16 corrigé).
 */
public final class SimulerLaFacturation {

    private final SeancesFacturables seances;
    private final ParametresDeFacturationRepository parametres;
    private final SequenceDeFacturation sequence;
    private final TauxDeTvaApplicable tva;
    private final UniteDeTravail uniteDeTravail;
    private final Clock horloge;

    public SimulerLaFacturation(SeancesFacturables seances, ParametresDeFacturationRepository parametres,
                                SequenceDeFacturation sequence, TauxDeTvaApplicable tva, UniteDeTravail uniteDeTravail,
                                Clock horloge) {
        this.seances = seances;
        this.parametres = parametres;
        this.sequence = sequence;
        this.tva = tva;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public Simulation simuler(CentreId centre, DemandeDeSimulation demande) {
        return uniteDeTravail.executer(() -> {
            PeriodeDeFacturation periode = demande.periode();
            var facturables = seances.dansLaPeriode(centre, periode);
            ParametresDeFacturation reglages = parametres.duCentre(centre).orElse(ParametresDeFacturation.PAR_DEFAUT);
            LocalDate aujourdhui = LocalDate.now(horloge);
            var numerotation = new NumerotationPrevisionnelle(reglages.modele(), aujourdhui,
                    sequence.courante(centre, aujourdhui.getYear()));
            return Simulation.etablir(centre, horloge.instant(), periode,
                    Regroupement.multiForfait(demande.regroupementMultiForfait()), facturables,
                    tva.aLaDate(centre, periode.fin()), numerotation);
        });
    }
}
