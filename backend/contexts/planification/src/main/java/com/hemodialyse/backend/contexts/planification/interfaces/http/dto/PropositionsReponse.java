package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.application.ProposerDesPlaces;
import com.hemodialyse.backend.contexts.planification.domain.PropositionsDePlace.Proposition;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;

import java.util.List;

public record PropositionsReponse(List<SalleReponse> salles, List<CreneauReponse> creneaux,
                                  List<PropositionReponse> propositions, List<CaseReponse> grille, List<String> alertes,
                                  boolean patientARisque) {

    public static PropositionsReponse depuis(ProposerDesPlaces.Reponse r) {
        return new PropositionsReponse(
                r.ressources().salles().stream().map(s -> new SalleReponse(s.id().valeur(), s.nom())).toList(),
                r.ressources().creneaux().stream().map(PropositionsReponse::creneau).toList(),
                r.propositions().stream().map(PropositionsReponse::proposition).toList(),
                r.grille().stream().map(c -> new CaseReponse(c.salle().valeur(), c.creneau().valeur(), c.jour(), c.capacite(),
                        c.occupes())).toList(),
                r.alertes().stream().map(Enum::name).toList(), r.patientARisque());
    }

    private static CreneauReponse creneau(RessourcesDuCentre.Creneau c) {
        return new CreneauReponse(c.id().valeur(), c.libelle(), c.ordre());
    }

    private static GenerateurReponse generateur(RessourcesDuCentre.Generateur g) {
        return new GenerateurReponse(g.id().valeur(), g.code(), g.salle().valeur());
    }

    private static PropositionReponse proposition(Proposition p) {
        return new PropositionReponse(new SalleReponse(p.salle().id().valeur(), p.salle().nom()), creneau(p.creneau()),
                generateur(p.generateur()), p.generateursAlternatifs().stream().map(PropositionsReponse::generateur).toList(),
                p.jours(), p.score(), p.raisons().stream().map(Enum::name).toList(),
                p.fermetures().stream().map(f -> new FermetureReponse(f.jour(), f.date(), f.motif())).toList());
    }
}
