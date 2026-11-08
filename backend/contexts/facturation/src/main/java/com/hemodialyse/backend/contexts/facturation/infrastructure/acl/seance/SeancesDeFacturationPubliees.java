package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.facturation.domain.seance.SeanceDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.seance.SeancesDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.StatutDeSeance;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ExclusionsDeSeancesFournies;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * ACL de la Facturation vers la Séance (ADR 0097, SE7c2, ARGENT) : l'exclusion, la réinclusion et le forfait imposé d'une séance passent par
 * {@link ExclusionsDeSeancesFournies} (fourni par la plateforme à partir de {@code seance.v1}). Remplace {@code SeancesDeFacturationJdbc}
 * (SQL direct sur {@code seances}). Traduction pure : statut du domaine en chaîne et retour, centre en identifiant ; aucune règle, aucun
 * montant recalculé. Les refus (422) viennent du port fourni.
 */
public class SeancesDeFacturationPubliees implements SeancesDeFacturation {

    private final ExclusionsDeSeancesFournies seances;

    public SeancesDeFacturationPubliees(ExclusionsDeSeancesFournies seances) {
        this.seances = seances;
    }

    @Override
    public Optional<SeanceDeFacturation> trouver(CentreId centre, UUID seance) {
        return seances.trouver(centre.valeur(), seance).map(s -> new SeanceDeFacturation(s.id(), centre, s.patient(), s.date(),
                new StatutDeSeance(s.statut()), s.facturee(),
                s.statutAvantExclusion() == null ? null : new StatutDeSeance(s.statutAvantExclusion()), s.absenceDeLExclusion()));
    }

    @Override
    public void exclure(CentreId centre, UUID seance, StatutDeSeance statutAvant) {
        seances.exclure(centre.valeur(), seance, statutAvant.valeur());
    }

    @Override
    public void lierALAbsence(CentreId centre, UUID seance, UUID absence) {
        seances.lierALAbsence(centre.valeur(), seance, absence);
    }

    @Override
    public void reinclure(CentreId centre, UUID seance, StatutDeSeance statutRetabli) {
        seances.reinclure(centre.valeur(), seance, statutRetabli.valeur());
    }

    @Override
    public void poserLeForfait(CentreId centre, UUID seance, ForfaitApplique forfait, String auteur, Instant le) {
        seances.poserLeForfait(centre.valeur(), seance, forfait.id(), forfait.libelle(), forfait.prixHt(), auteur, le);
    }
}
