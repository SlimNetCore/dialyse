package com.hemodialyse.backend.contexts.facturation.application.simulation;

import com.hemodialyse.backend.contexts.facturation.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.facturation.application.cloture.GardeDesMoisClos;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.seance.SeanceDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.seance.SeancesDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.Simulation;

import java.time.Clock;
import java.util.UUID;

/**
 * Cas d'usage « changer le forfait d'une séance » (RG-FAC-023) : un forfait du centre est imposé à la séance validée ou signée
 * non facturée (surcharge, RG-FAC-021), puis la simulation est recalculée, dans la même transaction.
 */
public final class ChangerLeForfaitDUneSeance {

    private final SeancesDeFacturation seances;
    private final ForfaitsDuCentre forfaits;
    private final SimulerLaFacturation simulation;
    private final UniteDeTravail uniteDeTravail;
    private final GardeDesMoisClos garde;
    private final Clock horloge;

    public ChangerLeForfaitDUneSeance(SeancesDeFacturation seances, ForfaitsDuCentre forfaits, SimulerLaFacturation simulation,
                                      UniteDeTravail uniteDeTravail, Clock horloge, GardeDesMoisClos garde) {
        this.seances = seances;
        this.forfaits = forfaits;
        this.simulation = simulation;
        this.uniteDeTravail = uniteDeTravail;
        this.garde = garde;
        this.horloge = horloge;
    }

    public Simulation executer(CentreId centre, UUID seanceId, UUID forfaitId, String auteur, DemandeDeSimulation demande) {
        return uniteDeTravail.executer(() -> {
            ForfaitApplique forfait = forfaits.forfait(centre, forfaitId)
                    .orElseThrow(() -> new IllegalArgumentException("Forfait introuvable pour ce centre"));
            SeanceDeFacturation seance = seances.trouver(centre, seanceId).orElseThrow(
                    () -> new IllegalStateException("Seance introuvable ou non modifiable pour changement forfait"));
            garde.exigerOuvert(centre, seance.date());
            seance.verifierQueLeForfaitPeutChanger();
            demande.periode();
            seances.poserLeForfait(centre, seanceId, forfait, auteur, horloge.instant());
            return simulation.simuler(centre, demande);
        });
    }
}
