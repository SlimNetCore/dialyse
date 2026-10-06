package com.hemodialyse.backend.domain.planning.optimisation.service;

import com.hemodialyse.backend.domain.infirmier.model.Presence.DonneesPresence;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service de domaine pur : empreinte (SHA-256) de l'état du centre lu par une optimisation. Une proposition n'est
 * applicable que si l'empreinte recalculée au moment de l'application est identique : si un patient, un roulement,
 * une absence ou un générateur a changé entre-temps, la proposition est périmée.
 * <p>
 * Seuls les éléments qui entrent dans le calcul comptent ; la durée de calcul, la stabilité et l'objectif, qui ne
 * modifient pas les données lues, n'en font pas partie.
 */
public final class EmpreinteOptimisation {

    private EmpreinteOptimisation() {
    }

    public static String calculer(DonneesOptimisation donnees, ParametresOptimisation parametres) {
        List<String> lignes = new ArrayList<>();
        lignes.add("P|" + parametres.perimetre() + "|" + parametres.debutSemaine() + "|" + parametres.nbSemaines());
        DonneesPresence presence = donnees.presence();
        DonneesPlanning planning = presence.planning();
        lignes.add("R|" + presence.patientsParInfirmier());
        lignes.add("J|" + trie(planning.joursOuverts()));
        lignes.add("I|" + trie(planning.sallesIsolement()));
        planning.salles().forEach(s -> lignes.add("S|" + s.id()));
        planning.creneaux().forEach(c -> lignes.add("C|" + c.id()));
        planning.generateurs().forEach(g -> lignes.add("G|" + g.id() + "|" + g.salleId()));
        planning.fermetures().forEach(f -> lignes.add("F|" + f.date()));
        donnees.patients().forEach(p -> lignes.add("A|" + p.patientId() + "|" + trie(p.jours()) + "|" + p.aRisque() + "|"
                + p.premierJour() + "|" + p.dernierJour() + "|"
                + (p.actuelle() == null ? "-" : p.actuelle().salleId() + "/" + p.actuelle().creneauId() + "/"
                + p.actuelle().generateurId()) + "|" + p.creneauPrefereId() + "|" + p.seancesAChoisir() + "|"
                + trie(p.transporteurs()) + "|" + trie(p.competencesRequises())));
        donnees.profils().values().forEach(pr -> lignes.add("Q|" + pr.infirmierId() + "|" + pr.tauxActivite() + "|"
                + trie(pr.competences())));
        donnees.indisponibilites().forEach(i -> lignes.add("X|" + i.generateurId() + "|" + i.debut() + "|" + i.fin()));
        donnees.temporaires().forEach(t -> lignes.add("T|" + t.id() + "|" + t.patientId() + "|" + t.date() + "|"
                + t.generateurId() + "|" + t.creneauId()));
        presence.infirmiers().forEach(i -> lignes.add("N|" + i.id() + "|" + i.qualification() + "|" + i.habiliteIsolement()));
        presence.affectations().forEach(a -> lignes.add("Y|" + a.id() + "|" + a.infirmierId() + "|" + a.salleId() + "|"
                + a.creneauId() + "|" + trie(a.jours())));
        presence.absences().forEach(a -> lignes.add("B|" + a.id() + "|" + a.infirmierId() + "|" + a.debut() + "|" + a.fin()));
        presence.remplacements().forEach(r -> lignes.add("M|" + r.id() + "|" + r.infirmierId() + "|" + r.date() + "|"
                + r.salleId() + "|" + r.creneauId()));
        String canonique = lignes.stream().sorted().collect(Collectors.joining("\n"));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonique.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private static String trie(java.util.Collection<?> valeurs) {
        if (valeurs == null) return "";
        return valeurs.stream().map(String::valueOf).sorted().collect(Collectors.joining(","));
    }
}
