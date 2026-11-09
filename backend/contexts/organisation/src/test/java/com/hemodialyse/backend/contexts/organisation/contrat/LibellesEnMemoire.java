package com.hemodialyse.backend.contexts.organisation.contrat;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.LibellesDeLOrganisation;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Faux en mémoire des libellés : il passe le MÊME contrat ({@link LibellesContrat}) que l'adapter JDBC. */
final class LibellesEnMemoire implements LibellesDeLOrganisation {

    private record Salle(UUID centre, String code, String nom) {
    }

    private record Creneau(UUID centre, String code, String libelle) {
    }

    private record Medecin(UUID centre, String nom, String prenom) {
    }

    private final Map<UUID, Salle> salles = new LinkedHashMap<>();
    private final Map<UUID, Creneau> creneaux = new LinkedHashMap<>();
    private final Map<UUID, Medecin> medecins = new LinkedHashMap<>();

    void salle(UUID centre, UUID id, String code, String nom) {
        salles.put(id, new Salle(centre, code, nom));
    }

    void creneau(UUID centre, UUID id, String code, String libelle) {
        creneaux.put(id, new Creneau(centre, code, libelle));
    }

    void medecin(UUID centre, UUID id, String nom, String prenom) {
        medecins.put(id, new Medecin(centre, nom, prenom));
    }

    @Override
    public Map<UUID, String> codesDesSalles(UUID centre, Collection<UUID> ids) {
        Map<UUID, String> sortie = new HashMap<>();
        for (UUID id : distincts(ids)) {
            Salle s = salles.get(id);
            if (s != null && s.centre().equals(centre) && s.code() != null) sortie.put(id, s.code());
        }
        return sortie;
    }

    @Override
    public Map<UUID, LibelleDeCreneau> creneauxDuCentre(UUID centre, Collection<UUID> ids) {
        Map<UUID, LibelleDeCreneau> sortie = new HashMap<>();
        for (UUID id : distincts(ids)) {
            Creneau c = creneaux.get(id);
            if (c != null && c.centre().equals(centre)) sortie.put(id, new LibelleDeCreneau(c.code(), c.libelle()));
        }
        return sortie;
    }

    @Override
    public Map<UUID, String> nomsDesSalles(UUID centre, Collection<UUID> ids) {
        Map<UUID, String> sortie = new HashMap<>();
        for (UUID id : distincts(ids)) {
            Salle s = salles.get(id);
            if (s != null && s.centre().equals(centre) && s.nom() != null) sortie.put(id, s.nom());
        }
        return sortie;
    }

    @Override
    public Map<UUID, String> libellesDesCreneaux(UUID centre, Collection<UUID> ids) {
        Map<UUID, String> sortie = new HashMap<>();
        for (UUID id : distincts(ids)) {
            Creneau c = creneaux.get(id);
            if (c != null && c.centre().equals(centre) && c.libelle() != null) sortie.put(id, c.libelle());
        }
        return sortie;
    }

    @Override
    public Map<UUID, String> nomsDesMedecins(UUID centre, Collection<UUID> ids) {
        Map<UUID, String> sortie = new HashMap<>();
        for (UUID id : distincts(ids)) {
            Medecin m = medecins.get(id);
            if (m == null || !m.centre().equals(centre)) continue;
            String libelle = (texte(m.nom()) + " " + texte(m.prenom())).trim();
            if (!libelle.isBlank()) sortie.put(id, libelle);
        }
        return sortie;
    }

    @Override
    public Set<UUID> medecinsCorrespondant(UUID centre, String terme) {
        Set<UUID> sortie = new HashSet<>();
        medecins.forEach((id, m) -> {
            String recherche = (texte(m.nom()) + " " + texte(m.prenom()) + " " + texte(m.prenom()) + " " + texte(m.nom()))
                    .toLowerCase(Locale.ROOT);
            if (m.centre().equals(centre) && correspond(terme, recherche, id)) sortie.add(id);
        });
        return sortie;
    }

    @Override
    public Set<UUID> creneauxCorrespondant(UUID centre, String terme) {
        Set<UUID> sortie = new HashSet<>();
        creneaux.forEach((id, c) -> {
            String recherche = (texte(c.code()) + " " + texte(c.libelle())).toLowerCase(Locale.ROOT);
            if (c.centre().equals(centre) && correspond(terme, recherche, id)) sortie.add(id);
        });
        return sortie;
    }

    /** SQL {@code LIKE '%terme%'} : {@code %} et {@code _} sont des jokers, comme dans l'adapter JDBC. */
    private static boolean correspond(String terme, String recherche, UUID id) {
        if (terme == null) return false;
        String motif = terme.trim().toLowerCase(Locale.ROOT);
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(".*" + joker(motif) + ".*", java.util.regex.Pattern.DOTALL);
        return p.matcher(recherche).matches() || p.matcher(id.toString().toLowerCase(Locale.ROOT)).matches();
    }

    private static String joker(String motif) {
        StringBuilder b = new StringBuilder();
        for (char c : motif.toCharArray()) {
            if (c == '%') b.append(".*");
            else if (c == '_') b.append('.');
            else b.append(java.util.regex.Pattern.quote(String.valueOf(c)));
        }
        return b.toString();
    }

    private static String texte(String valeur) {
        return valeur == null ? "" : valeur;
    }

    private static Set<UUID> distincts(Collection<UUID> ids) {
        Set<UUID> sortie = new HashSet<>();
        if (ids != null) ids.stream().filter(Objects::nonNull).forEach(sortie::add);
        return sortie;
    }
}
