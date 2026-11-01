package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.Semaine;

import java.time.LocalDate;
import java.util.List;

public record SemaineReponse(LocalDate debut, LocalDate fin, List<JourReponse> jours, List<SalleReponse> salles,
                             List<CreneauReponse> creneaux, List<CelluleReponse> cellules,
                             List<ConflitReponse> conflits,
                             int patientsAReplanifier, List<SeanceSansCaseReponse> seancesSansCase) {
    public static SemaineReponse depuis(Semaine s) {
        return new SemaineReponse(s.debut(), s.fin(),
                s.jours().stream().map(j -> new JourReponse(j.jour(), j.date(), j.ouvertHebdomadaire(), j.fermeture())).toList(),
                s.salles().stream().map(x -> new SalleReponse(x.id().valeur(), x.nom())).toList(),
                s.creneaux().stream().map(x -> new CreneauReponse(x.id().valeur(), x.libelle(), x.ordre())).toList(),
                s.cases().stream().map(c -> new CelluleReponse(c.salle().valeur(), c.creneau().valeur(), c.jour(), c.capacite(),
                        c.occupants().stream().map(o -> new OccupantReponse(o.patient().valeur(), o.nom(), o.generateur(), o.aRisque(),
                                o.libereLe(), o.temporaire(), o.realiseeHorsPlanning(), o.dejaRealiseeLe())).toList())).toList(),
                s.conflits().stream().map(c -> new ConflitReponse(c.type(), c.salle() == null ? null : c.salle().valeur(),
                        c.creneau() == null ? null : c.creneau().valeur(), c.jour(), c.patients())).toList(),
                s.patientsAReplanifier(),
                s.seancesSansCase().stream().map(x -> new SeanceSansCaseReponse(x.patient().valeur(), x.nom(), x.date(), x.jour())).toList());
    }
}
