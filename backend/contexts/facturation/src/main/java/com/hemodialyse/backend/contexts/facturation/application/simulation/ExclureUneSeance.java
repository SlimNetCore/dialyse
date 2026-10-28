package com.hemodialyse.backend.contexts.facturation.application.simulation;

import com.hemodialyse.backend.contexts.facturation.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.facturation.application.cloture.GardeDesMoisClos;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.seance.Exclusion;
import com.hemodialyse.backend.contexts.facturation.domain.seance.MotifDAbsence;
import com.hemodialyse.backend.contexts.facturation.domain.seance.SeanceDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.seance.SeancesDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.Simulation;

import java.util.UUID;

/**
 * Cas d'usage « exclure une séance de la facturation » (RG-FAC-023, KB-FAC-13) : la séance validée ou signée non facturée
 * passe à {@code ABSENT} et une absence de patient est déclarée avec le motif (obligatoire ; {@code AUTRE} exige un
 * commentaire), au nom de l'utilisateur de la session. La simulation est ensuite recalculée, dans la même transaction : si
 * l'exclusion ou la déclaration d'absence est refusée, rien n'est modifié.
 */
public final class ExclureUneSeance {

    private final SeancesDeFacturation seances;
    private final AbsencesDesPatients absences;
    private final SimulerLaFacturation simulation;
    private final UniteDeTravail uniteDeTravail;
    private final GardeDesMoisClos garde;

    public ExclureUneSeance(SeancesDeFacturation seances, AbsencesDesPatients absences, SimulerLaFacturation simulation,
                            UniteDeTravail uniteDeTravail, GardeDesMoisClos garde) {
        this.seances = seances;
        this.absences = absences;
        this.simulation = simulation;
        this.uniteDeTravail = uniteDeTravail;
        this.garde = garde;
    }

    public Simulation executer(CentreId centre, UUID seanceId, Commande commande, DemandeDeSimulation demande) {
        return uniteDeTravail.executer(() -> {
            SeanceDeFacturation seance = seances.trouver(centre, seanceId)
                    .orElseThrow(() -> new IllegalStateException("Seance introuvable ou non modifiable pour exclusion"));
            garde.exigerOuvert(centre, seance.date());
            Exclusion exclusion = seance.exclure(commande.motif(), commande.commentaire());
            demande.periode();
            seances.exclure(centre, seanceId, exclusion.statutAvant());
            UUID absence = absences.declarer(centre, seance.patientId(), seance.date(), exclusion.motif(),
                    exclusion.commentaire(), commande.auteur());
            seances.lierALAbsence(centre, seanceId, absence);
            return simulation.simuler(centre, demande);
        });
    }

    /**
     * @param auteur l'utilisateur de la session (tracé par l'absence), nul si la session ne le donne pas
     */
    public record Commande(MotifDAbsence motif, String commentaire, UUID auteur) {
    }
}
