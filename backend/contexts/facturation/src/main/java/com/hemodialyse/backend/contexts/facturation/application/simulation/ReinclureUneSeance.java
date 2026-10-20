package com.hemodialyse.backend.contexts.facturation.application.simulation;

import com.hemodialyse.backend.contexts.facturation.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.seance.Reinclusion;
import com.hemodialyse.backend.contexts.facturation.domain.seance.SeanceDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.seance.SeancesDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.Simulation;

import java.util.UUID;

/**
 * Cas d'usage « réinclure dans la facturation une séance exclue » (KB-FAC-13) : tant qu'elle n'est pas facturée, la séance
 * retrouve son statut d'avant l'exclusion et l'absence que l'exclusion avait déclarée est retirée. La simulation est ensuite
 * recalculée, dans la même transaction.
 */
public final class ReinclureUneSeance {

    private final SeancesDeFacturation seances;
    private final AbsencesDesPatients absences;
    private final SimulerLaFacturation simulation;
    private final UniteDeTravail uniteDeTravail;

    public ReinclureUneSeance(SeancesDeFacturation seances, AbsencesDesPatients absences, SimulerLaFacturation simulation,
                              UniteDeTravail uniteDeTravail) {
        this.seances = seances;
        this.absences = absences;
        this.simulation = simulation;
        this.uniteDeTravail = uniteDeTravail;
    }

    public Simulation executer(CentreId centre, UUID seanceId, DemandeDeSimulation demande) {
        return uniteDeTravail.executer(() -> {
            SeanceDeFacturation seance = seances.trouver(centre, seanceId)
                    .orElseThrow(() -> new IllegalStateException("Seance introuvable ou non reinclusable"));
            Reinclusion reinclusion = seance.reinclure();
            demande.periode();
            seances.reinclure(centre, seanceId, reinclusion.statutRetabli());
            if (reinclusion.absenceARetirer() != null) {
                absences.retirer(centre, reinclusion.absenceARetirer());
            }
            return simulation.simuler(centre, demande);
        });
    }
}
