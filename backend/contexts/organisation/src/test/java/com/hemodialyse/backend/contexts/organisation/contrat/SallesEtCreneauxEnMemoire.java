package com.hemodialyse.backend.contexts.organisation.contrat;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.DefinirLesSallesDIsolement;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.SallesEtCreneauxDuCentre;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Faux en mémoire : il passe le MÊME contrat ({@link SallesEtCreneauxContrat}) que l'adapter JDBC. */
final class SallesEtCreneauxEnMemoire implements SallesEtCreneauxDuCentre, DefinirLesSallesDIsolement {

    private record Salle(UUID centre, String code, String nom, boolean isolement, Integer capacite) {
    }

    private record Creneau(UUID centre, String code, String libelle) {
    }

    private final Map<UUID, Salle> salles = new LinkedHashMap<>();
    private final Map<UUID, Creneau> creneaux = new LinkedHashMap<>();

    void salle(UUID centre, UUID id, String code, String nom, boolean isolement, Integer capacite) {
        salles.put(id, new Salle(centre, code, nom, isolement, capacite));
    }

    void creneau(UUID centre, UUID id, String code, String libelle) {
        creneaux.put(id, new Creneau(centre, code, libelle));
    }

    private static SalleDuCentre vue(UUID id, Salle s) {
        return new SalleDuCentre(id, s.code(), s.nom(), s.isolement(), s.capacite());
    }

    @Override
    public List<SalleDuCentre> sallesParCode(UUID centre) {
        return salles.entrySet().stream().filter(e -> e.getValue().centre().equals(centre))
                .sorted(Comparator.comparing(e -> e.getValue().code())).map(e -> vue(e.getKey(), e.getValue())).toList();
    }

    @Override
    public PageDeSalles pageDesSalles(UUID centre, int taille, long decalage) {
        List<SalleDuCentre> toutes = sallesParCode(centre);
        List<SalleDuCentre> page = new ArrayList<>(toutes.stream().skip(decalage).limit(taille).toList());
        return new PageDeSalles(page, toutes.size());
    }

    @Override
    public Optional<SalleDuCentre> salleDuCentre(UUID centre, UUID salle) {
        Salle s = salles.get(salle);
        return s != null && s.centre().equals(centre) ? Optional.of(vue(salle, s)) : Optional.empty();
    }

    @Override
    public List<CreneauDuCentre> creneauxParCode(UUID centre) {
        return creneaux.entrySet().stream().filter(e -> e.getValue().centre().equals(centre))
                .sorted(Comparator.comparing(e -> e.getValue().code()))
                .map(e -> new CreneauDuCentre(e.getKey(), e.getValue().libelle())).toList();
    }

    @Override
    public boolean contientLeCreneau(UUID centre, UUID creneau) {
        Creneau c = creneaux.get(creneau);
        return c != null && c.centre().equals(centre);
    }

    @Override
    public int nombreDeCreneaux(UUID centre) {
        return (int) creneaux.values().stream().filter(c -> c.centre().equals(centre)).count();
    }

    @Override
    public void definir(UUID centre, Set<UUID> sallesDIsolement) {
        salles.replaceAll((id, s) -> !s.centre().equals(centre) ? s
                : new Salle(s.centre(), s.code(), s.nom(), sallesDIsolement.contains(id), s.capacite()));
    }
}
