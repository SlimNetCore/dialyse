package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;

/**
 * Port sortant (DOC5f, ADR 0198) : les patients du centre tels que la liste des patients les imprime, triés par nom puis par prénom (le
 * tri est celui de la base, comme l'ancienne requête du gabarit). Lecture seule, bornée au centre ; un centre sans patient donne une liste
 * vide.
 */
public interface ListeDesPatientsAImprimer {

    /** Un patient de la liste imprimée (valeurs telles que la fiche les porte : une valeur absente reste nulle). */
    record PatientImprime(String codePatient, String nom, String prenom, String sexe, String groupeSanguin, LocalDate dateNaissance,
                          LocalDate dateAdmission, String numeroAssurance, String telMobile, String etatPatient) {
    }

    List<PatientImprime> duCentre(CentreId centre);
}
