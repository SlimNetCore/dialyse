package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyorganisation;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre;
import com.hemodialyse.backend.contexts.planification.application.SalleDuCentre;
import com.hemodialyse.backend.contexts.planification.application.VueDesSalles;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * ACL vers Organisation ({@code salle}, capacité) et GMAO (générateurs par {@link GenerateursDuCentre}) : les générateurs affectés à
 * une salle sont ceux de dialyse non supprimés, hors réformés et désactivés (les générateurs en maintenance ou hors service restent
 * comptés, KB-PLN-64).
 */
public class VueDesSallesLegacy implements VueDesSalles, SalleDuCentre {

    private static final Set<String> STATUTS_SANS_AFFECTATION = Set.of("REFORME", "DESACTIF");

    private final JdbcTemplate jdbc;
    private final GenerateursDuCentre generateurs;

    public VueDesSallesLegacy(JdbcTemplate jdbc, GenerateursDuCentre generateurs) {
        this.jdbc = jdbc;
        this.generateurs = generateurs;
    }

    private record Ligne(UUID id, String code, String nom, boolean isolement, Integer capacite) {
    }

    @Override
    public Page lister(CentreId centre, Pagination pagination) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM salle WHERE center_id = ?", Long.class, centre.valeur());
        List<Ligne> lignes = jdbc.query("SELECT id, code, nom, isolement, capacite FROM salle WHERE center_id = ? "
                        + "ORDER BY code LIMIT ? OFFSET ?",
                (rs, i) -> new Ligne(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("nom"),
                        SallesDuCentreJdbc.OUI.equals(rs.getString("isolement")),
                        rs.getObject("capacite") == null ? null : rs.getInt("capacite")),
                centre.valeur(), pagination.taille(), pagination.decalage());
        Map<UUID, List<GenerateurVue>> parSalle = new HashMap<>();
        generateurs.duCentre(centre.valeur()).stream()
                .filter(g -> g.salleId() != null && !STATUTS_SANS_AFFECTATION.contains(g.statut()))
                .sorted(Comparator.comparing(GenerateursDuCentre.Generateur::code))
                .forEach(g -> parSalle.computeIfAbsent(g.salleId(), k -> new ArrayList<>())
                        .add(new GenerateurVue(g.id(), g.code(), g.designation(), g.statut())));
        List<SalleVue> items = lignes.stream().map(l -> new SalleVue(l.id(), l.code(), l.nom(), l.isolement(), l.capacite(),
                parSalle.getOrDefault(l.id(), List.of()))).toList();
        return new Page(items, total == null ? 0 : total);
    }

    @Override
    public Optional<SalleVue> parIdentifiant(CentreId centre, UUID salle) {
        return jdbc.query("SELECT id, code, nom, isolement, capacite FROM salle WHERE center_id = ? AND id = ?",
                (rs, i) -> new Ligne(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("nom"),
                        SallesDuCentreJdbc.OUI.equals(rs.getString("isolement")),
                        rs.getObject("capacite") == null ? null : rs.getInt("capacite")),
                centre.valeur(), salle).stream().findFirst().map(l -> new SalleVue(l.id(), l.code(), l.nom(), l.isolement(),
                l.capacite(), generateurs.duCentre(centre.valeur()).stream()
                .filter(g -> salle.equals(g.salleId()) && !STATUTS_SANS_AFFECTATION.contains(g.statut()))
                .sorted(Comparator.comparing(GenerateursDuCentre.Generateur::code))
                .map(g -> new GenerateurVue(g.id(), g.code(), g.designation(), g.statut())).toList()));
    }
}
