package com.hemodialyse.backend.infrastructure.acl.facturation;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SeancesAFacturerFournies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesAFacturerPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesAFacturerPubliees.PatientDeLaSeancePublie;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fournisseur de la Facturation (ADR 0097, SE7c2, ARGENT) : les séances à facturer du langage publié de la Facturation viennent de
 * {@link SeancesAFacturerPubliees} (seance.v1). Logé dans la plateforme : le module Seance dépend d'Absences-patients, qui dépend de
 * Facturation (cycle Maven). Traduction champ à champ de deux langages publiés, aucune règle, aucun montant recalculé (le prix imposé est
 * copié à l'identique).
 */
@Component
public class SeancesFacturablesParLaSeance implements SeancesAFacturerFournies {

    private final SeancesAFacturerPubliees seances;

    public SeancesFacturablesParLaSeance(SeancesAFacturerPubliees seances) {
        this.seances = seances;
    }

    @Override
    public List<SeanceAFacturerFournie> dansLaPeriode(UUID centre, LocalDate debut, LocalDate fin) {
        return seances.dansLaPeriode(centre, debut, fin).stream()
                .map(s -> new SeanceAFacturerFournie(s.id(), s.date(), s.statut(), s.forfaitImposeId(), s.libelleImpose(),
                        s.prixHtImpose(), patient(s.patient())))
                .toList();
    }

    private static PatientFourni patient(PatientDeLaSeancePublie p) {
        return new PatientFourni(p.id(), p.code(), p.nom(), p.prenom(), p.etat(), p.numeroAssurance(), p.centrePayeur(), p.agence());
    }
}
