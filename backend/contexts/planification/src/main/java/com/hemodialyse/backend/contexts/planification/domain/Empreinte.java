package com.hemodialyse.backend.contexts.planification.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Empreinte de l'état du centre lu par une optimisation (P22) : SHA-256 d'une description canonique et triée des données. Une
 * proposition ne s'applique que si l'empreinte actuelle est celle du calcul. Les noms (patients, infirmiers) n'y entrent pas.
 * Élargie le 28/10 aux séances réalisées de l'horizon et aux préférences de planification (KB-PLN-95) ; l'ancienne empreinte
 * (sans elles) n'est plus produite : une proposition calculée avant la montée de version est périmée, à relancer.
 */
public record Empreinte(String valeur) {

    public Empreinte {
        if (valeur == null || valeur.isBlank()) throw new SaisieInvalide("Empreinte requise");
    }

    public static Empreinte calculer(DonneesDOptimisation donnees, ParametresDOptimisation parametres) {
        List<String> lignes = new ArrayList<>();
        lignes.add("P|" + parametres.perimetre() + "|" + parametres.debutSemaine() + "|" + parametres.nbSemaines());
        RessourcesDuCentre ressources = donnees.ressources();
        Presence.Personnel personnel = donnees.personnel();
        lignes.add("R|" + personnel.ratio().patientsParInfirmier());
        lignes.add("J|" + trie(ressources.joursOuverts()));
        lignes.add("I|" + trie(ressources.salles().stream().filter(RessourcesDuCentre.Salle::isolement)
                .map(s -> s.id().valeur()).toList()));
        ressources.salles().forEach(s -> lignes.add("S|" + s.id().valeur()));
        ressources.creneaux().forEach(c -> lignes.add("C|" + c.id().valeur()));
        ressources.generateurs().forEach(g -> lignes.add("G|" + g.id().valeur() + "|" + g.salle().valeur()));
        ressources.fermetures().forEach(f -> lignes.add("F|" + f.date()));
        donnees.patients().forEach(p -> lignes.add("A|" + p.patient().valeur() + "|" + trie(p.jours()) + "|" + p.aRisque() + "|"
                + p.premierJour() + "|" + p.dernierJour() + "|"
                + (p.actuelle() == null ? "-" : valeur(p.actuelle().salle() == null ? null : p.actuelle().salle().valeur()) + "/"
                + valeur(p.actuelle().creneau() == null ? null : p.actuelle().creneau().valeur()) + "/"
                + valeur(p.actuelle().generateur() == null ? null : p.actuelle().generateur().valeur()))
                + "|" + (p.creneauPrefere() == null ? null : p.creneauPrefere().valeur()) + "|" + p.seancesAChoisir() + "|"
                + trie(p.transporteurs()) + "|" + trie(p.competencesRequises())));
        donnees.profils().values().forEach(pr -> lignes.add("Q|" + pr.infirmier().valeur() + "|" + pr.tauxActivite() + "|"
                + trie(pr.competences())));
        donnees.indisponibilites().forEach(i -> lignes.add("X|" + i.generateur().valeur() + "|" + i.debut() + "|" + i.fin()));
        donnees.temporaires().forEach(t -> lignes.add("T|" + t.id().valeur() + "|" + t.patient().valeur() + "|" + t.date() + "|"
                + t.generateur().valeur() + "|" + t.creneau().valeur()));
        personnel.infirmiers().forEach(i -> lignes.add("N|" + i.id() + "|" + i.qualification() + "|" + i.habiliteIsolement()));
        personnel.affectations().forEach(a -> lignes.add("Y|" + a.infirmier() + "|" + a.salle() + "|" + a.creneau() + "|"
                + trie(a.jours())));
        personnel.absences().forEach(a -> lignes.add("B|" + a.infirmier() + "|" + a.debut() + "|" + a.fin()));
        personnel.remplacements().forEach(r -> lignes.add("M|" + r.id() + "|" + r.infirmier() + "|" + r.date() + "|" + r.salle()
                + "|" + r.creneau()));
        donnees.seances().forEach(s -> lignes.add("E|" + s.patient().valeur() + "|" + s.date() + "|"
                + (s.salle() == null ? null : s.salle().valeur()) + "|" + (s.creneau() == null ? null : s.creneau().valeur()) + "|"
                + (s.generateur() == null ? null : s.generateur().valeur())));
        donnees.preferences().forEach(p -> lignes.add("Z|" + p.patient().valeur() + "|"
                + (p.creneauPrefere() == null ? null : p.creneauPrefere().valeur()) + "|" + p.seancesParSemaine() + "|"
                + p.joursAChoisir()));
        String canonique = lignes.stream().sorted().collect(Collectors.joining("\n"));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonique.getBytes(StandardCharsets.UTF_8));
            return new Empreinte(HexFormat.of().formatHex(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private static String valeur(Object v) {
        return String.valueOf(v);
    }

    private static String trie(Collection<?> valeurs) {
        if (valeurs == null) return "";
        return valeurs.stream().map(String::valueOf).sorted().collect(Collectors.joining(","));
    }
}
