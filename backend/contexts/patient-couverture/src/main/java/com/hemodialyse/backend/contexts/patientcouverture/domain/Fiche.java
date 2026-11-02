package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.time.LocalDate;

/**
 * Contenu saisi de la fiche d'un patient (création ou modification).
 *
 * @param etat           nul : à la création {@code PERMANENT}, à la modification l'état courant est conservé
 * @param dateEvenement  date de sortie (états de sortie) ou de fin de séjour (séjour limité) ; nulle sinon
 */
public record Fiche(Identite identite, Coordonnees coordonnees, SuiviMedical suivi, Couverture couverture,
                    Affectation affectation, LocalDate dateAdmission, EtatDuPatient etat, LocalDate dateEvenement) {

    public Fiche {
        if (identite == null || coordonnees == null || suivi == null || couverture == null || affectation == null) {
            throw new SaisieInvalide("Fiche incomplète");
        }
        if (dateAdmission == null) throw new SaisieInvalide("Date admission obligatoire");
    }

    Fiche avecCouverture(Couverture nouvelle) {
        return new Fiche(identite, coordonnees, suivi, nouvelle, affectation, dateAdmission, etat, dateEvenement);
    }

    Fiche avecEtat(EtatDuPatient nouvelEtat) {
        return new Fiche(identite, coordonnees, suivi, couverture, affectation, dateAdmission, nouvelEtat, dateEvenement);
    }
}
