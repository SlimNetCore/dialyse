package com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.DefinirLesSallesDIsolement;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.SallesEtCreneauxDuCentre;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.SallesEtCreneauxDuCentre.SalleDuCentre;
import com.hemodialyse.backend.contexts.planification.application.SallesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Traducteur vers le langage publié d'Organisation (O4 lot D, ADR 0110) : les salles d'un centre et leur marque d'isolement. La marque
 * s'écrit par la commande {@link DefinirLesSallesDIsolement}, dans la transaction de l'appelant (aucune transaction propre ici).
 */
public class SallesDuCentrePubliees implements SallesDuCentre {

    private final SallesEtCreneauxDuCentre salles;
    private final DefinirLesSallesDIsolement isolement;

    public SallesDuCentrePubliees(SallesEtCreneauxDuCentre salles, DefinirLesSallesDIsolement isolement) {
        this.salles = salles;
        this.isolement = isolement;
    }

    @Override
    public Set<SalleId> toutes(CentreId centre) {
        return salles.sallesParCode(centre.valeur()).stream().map(s -> new SalleId(s.id())).collect(Collectors.toSet());
    }

    @Override
    public Set<SalleId> sallesDIsolement(CentreId centre) {
        return salles.sallesParCode(centre.valeur()).stream().filter(SalleDuCentre::estSalleDIsolement)
                .map(s -> new SalleId(s.id())).collect(Collectors.toSet());
    }

    @Override
    public void marquerIsolement(CentreId centre, Set<SalleId> sallesDIsolement) {
        isolement.definir(centre.valeur(), sallesDIsolement.stream().map(SalleId::valeur).collect(Collectors.toSet()));
    }
}
