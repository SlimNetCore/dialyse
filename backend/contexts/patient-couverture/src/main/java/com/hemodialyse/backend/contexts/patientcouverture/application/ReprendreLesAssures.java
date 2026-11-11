package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.application.AssuresDuCentre.Assure;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAssures;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reprise des assurés de l'outil de migration historique (ADR 0134) : mêmes tables {@code assure}, mêmes colonnes. Une reprise
 * n'efface jamais une donnée : seules les valeurs renseignées dans le fichier remplacent celles de l'assuré existant. La garde de
 * centre (numéro d'un autre centre) est décidée par l'appelant, qui contrôle tout le fichier avant d'écrire.
 */
public class ReprendreLesAssures implements RepriseDesAssures {

    private final AssuresDuCentre assures;

    public ReprendreLesAssures(AssuresDuCentre assures) {
        this.assures = assures;
    }

    @Override
    public Optional<AssureConnu> trouver(String numeroAssurance) {
        return assures.trouver(numeroAssurance).map(a -> new AssureConnu(a.numeroAssurance(), a.centre()));
    }

    @Override
    public void reprendre(UUID centre, ReportDeLAssure report) {
        Optional<Assure> existant = assures.trouver(report.numeroAssurance());
        assures.declarer(new Assure(report.numeroAssurance(), report.nom(),
                valeur(report.prenom(), existant.map(Assure::prenom)), valeur(report.sexe(), existant.map(Assure::sexe)),
                valeur(report.dateNaissance(), existant.map(Assure::dateNaissance)),
                valeur(report.telPersonnel(), existant.map(Assure::telPersonnel)),
                valeur(report.telMobile(), existant.map(Assure::telMobile)),
                valeur(report.telBureau(), existant.map(Assure::telBureau)),
                valeur(report.adresse(), existant.map(Assure::adresse)),
                valeur(report.groupeSanguin(), existant.map(Assure::groupeSanguin)), centre));
    }

    @Override
    public Optional<AssureConnu> trouverSansCasse(String numeroAssurance) {
        return assures.trouver(numeroAssurance).or(() -> assures.trouverSansCasse(numeroAssurance))
                .map(a -> new AssureConnu(a.numeroAssurance(), a.centre()));
    }

    /** Mémento v1 : les valeurs de l'assuré en {@code clé=valeur&…} encodées en URL (une valeur vide n'a pas de clé). */
    @Override
    public Optional<String> memoriser(String numeroAssurance) {
        return assures.trouver(numeroAssurance).map(a -> {
            Map<String, String> valeurs = new LinkedHashMap<>();
            valeurs.put("v", "1");
            valeurs.put("nom", a.nom());
            valeurs.put("prenom", a.prenom());
            valeurs.put("sexe", a.sexe());
            valeurs.put("dateNaissance", a.dateNaissance() == null ? null : a.dateNaissance().toString());
            valeurs.put("telPersonnel", a.telPersonnel());
            valeurs.put("telMobile", a.telMobile());
            valeurs.put("telBureau", a.telBureau());
            valeurs.put("adresse", a.adresse());
            valeurs.put("groupeSanguin", a.groupeSanguin());
            return valeurs.entrySet().stream().filter(e -> e.getValue() != null)
                    .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
        });
    }

    @Override
    public void restaurer(UUID centre, String numeroAssurance, String memento) {
        Map<String, String> valeurs = new HashMap<>();
        for (String champ : memento.split("&")) {
            int egal = champ.indexOf('=');
            valeurs.put(champ.substring(0, egal), URLDecoder.decode(champ.substring(egal + 1), StandardCharsets.UTF_8));
        }
        if (!"1".equals(valeurs.get("v"))) throw new IllegalArgumentException("Mémento d'assuré de version inconnue");
        Optional<Assure> actuel = assures.trouver(numeroAssurance);
        if (actuel.isEmpty() || !centre.equals(actuel.get().centre())) return;
        String naissance = valeurs.get("dateNaissance");
        assures.mettreAJour(new Assure(numeroAssurance, valeurs.get("nom"), valeurs.get("prenom"), valeurs.get("sexe"),
                naissance == null ? null : LocalDate.parse(naissance), valeurs.get("telPersonnel"), valeurs.get("telMobile"),
                valeurs.get("telBureau"), valeurs.get("adresse"), valeurs.get("groupeSanguin"), actuel.get().centre()));
    }

    private static <T> T valeur(T renseignee, Optional<T> actuelle) {
        return renseignee != null ? renseignee : actuelle.orElse(null);
    }
}
