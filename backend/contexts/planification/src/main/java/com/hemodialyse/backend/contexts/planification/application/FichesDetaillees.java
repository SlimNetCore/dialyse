package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.application.ComptesUtilisateurs.Compte;
import com.hemodialyse.backend.contexts.planification.application.RoulementsDesInfirmiers.AffectationLue;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Assemble les fiches lues avec leur roulement et leur compte (lecture seule).
 */
public class FichesDetaillees {
    private final RoulementsDesInfirmiers roulements;
    private final ComptesUtilisateurs comptes;

    public FichesDetaillees(RoulementsDesInfirmiers roulements, ComptesUtilisateurs comptes) {
        this.roulements = roulements;
        this.comptes = comptes;
    }

    public FicheDetaillee de(Infirmier fiche) {
        return de(List.of(fiche)).getFirst();
    }

    public List<FicheDetaillee> de(List<Infirmier> fiches) {
        if (fiches.isEmpty()) {
            return List.of();
        }
        CentreId centre = fiches.getFirst().centre();
        Map<InfirmierId, List<AffectationLue>> roulement = roulements.deLaListe(centre, fiches.stream().map(Infirmier::id).toList());
        Map<CompteId, Compte> parCompte = comptes.parIds(fiches.stream().flatMap(f -> f.compte().stream()).toList()).stream()
                .collect(Collectors.toMap(Compte::id, Function.identity()));
        return fiches.stream().map(f -> new FicheDetaillee(f.id().valeur(), f.matricule().valeur(), f.identite().nom(),
                f.identite().prenom(), f.identite().telephone(), f.qualification(), f.habiliteIsolement(), f.actif(),
                roulement.getOrDefault(f.id(), List.of()), f.compte().map(parCompte::get).orElse(null))).toList();
    }
}
