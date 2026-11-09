package com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.SallesEtCreneauxDuCentre;
import com.hemodialyse.backend.contexts.planification.application.VueDesSalles;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Traducteur vers Organisation ({@link SallesEtCreneauxDuCentre}, capacité, O4 lot D, ADR 0110) et GMAO (générateurs par
 * {@link GenerateursDuCentre}) : les générateurs affectés à une salle sont ceux de dialyse non supprimés, hors réformés et désactivés
 * (les générateurs en maintenance ou hors service restent comptés, KB-PLN-64).
 */
public class VueDesSallesPubliees implements VueDesSalles, com.hemodialyse.backend.contexts.planification.application.SalleDuCentre {

    private static final Set<String> STATUTS_SANS_AFFECTATION = Set.of("REFORME", "DESACTIF");

    private final SallesEtCreneauxDuCentre organisation;
    private final GenerateursDuCentre generateurs;

    public VueDesSallesPubliees(SallesEtCreneauxDuCentre organisation, GenerateursDuCentre generateurs) {
        this.organisation = organisation;
        this.generateurs = generateurs;
    }

    @Override
    public Page lister(CentreId centre, Pagination pagination) {
        SallesEtCreneauxDuCentre.PageDeSalles lues = organisation.pageDesSalles(centre.valeur(), pagination.taille(),
                pagination.decalage());
        Map<UUID, List<GenerateurVue>> parSalle = new HashMap<>();
        generateurs.duCentre(centre.valeur()).stream()
                .filter(g -> g.salleId() != null && !STATUTS_SANS_AFFECTATION.contains(g.statut()))
                .sorted(Comparator.comparing(GenerateursDuCentre.Generateur::code))
                .forEach(g -> parSalle.computeIfAbsent(g.salleId(), k -> new ArrayList<>())
                        .add(new GenerateurVue(g.id(), g.code(), g.designation(), g.statut())));
        List<SalleVue> items = lues.salles().stream().map(l -> new SalleVue(l.id(), l.code(), l.nom(), l.estSalleDIsolement(),
                l.capacite(), parSalle.getOrDefault(l.id(), List.of()))).toList();
        return new Page(items, lues.total());
    }

    @Override
    public Optional<SalleVue> parIdentifiant(CentreId centre, UUID salle) {
        return organisation.salleDuCentre(centre.valeur(), salle).map(l -> new SalleVue(l.id(), l.code(), l.nom(),
                l.estSalleDIsolement(), l.capacite(), generateurs.duCentre(centre.valeur()).stream()
                .filter(g -> salle.equals(g.salleId()) && !STATUTS_SANS_AFFECTATION.contains(g.statut()))
                .sorted(Comparator.comparing(GenerateursDuCentre.Generateur::code))
                .map(g -> new GenerateurVue(g.id(), g.code(), g.designation(), g.statut())).toList()));
    }
}
