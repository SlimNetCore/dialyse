package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.time.LocalDate;

/** Jeux d'essai des fiches (tests du domaine et de l'application). */
public final class Fiches {

    public static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 2);
    public static final LocalDate ADMISSION = LocalDate.of(2026, 1, 10);

    private Fiches() {
    }

    public static Fiche nominale() {
        return nominale("PC1-001", null, null);
    }

    public static Fiche nominale(String numero, EtatDuPatient etat, LocalDate dateEvenement) {
        return avec(numero, etat, dateEvenement, ADMISSION, LocalDate.of(1970, 5, 17));
    }

    public static Fiche avec(String numero, EtatDuPatient etat, LocalDate dateEvenement, LocalDate admission, LocalDate naissance) {
        return new Fiche(
                new Identite("M", "Benali", "Karim", "M", "O+", 2, naissance, "Alger", "MARIE"),
                new Coordonnees("Chauffeur", "1 rue des Oliviers", "0555 12 34 56", null, null, "Karim@Example.org"),
                new SuiviMedical(false, false, null, false, null, false, null, null),
                new Couverture(new NumeroAssurance(numero), null, null, null, null, null, null),
                new Affectation(null, null, null, null, null, null, null, null),
                admission, etat, dateEvenement);
    }

    public static Fiche tiers(String numero, String numeroAssure) {
        Fiche f = nominale(numero, null, null);
        return new Fiche(f.identite(), f.coordonnees(), f.suivi(),
                new Couverture(f.couverture().numeroAssurance(), "ENFANT", null, numeroAssure,
                        new Couverture.AssureSurLaFiche("F", "Benali", "Leila", "1965-01-01", null, null, null, null, null),
                        null, null),
                f.affectation(), f.dateAdmission(), f.etat(), f.dateEvenement());
    }
}
