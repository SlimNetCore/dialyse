package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.application.AssuresDuCentre.Assure;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAssures;

import java.util.Optional;
import java.util.UUID;

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

    private static <T> T valeur(T renseignee, Optional<T> actuelle) {
        return renseignee != null ? renseignee : actuelle.orElse(null);
    }
}
