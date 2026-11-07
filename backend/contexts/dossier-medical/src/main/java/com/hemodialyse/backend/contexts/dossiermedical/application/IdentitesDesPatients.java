package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;

/** Port sortant : l'état civil du patient du centre (fiche tenue par Patient et couverture, hors contexte), lu pour l'export du dossier. */
public interface IdentitesDesPatients {

    /** L'identité du patient dans CE centre ; inconnu ou d'un autre centre : vide. Valeurs telles que saisies (nulles ou vides possibles). */
    Optional<Identite> duPatient(CentreId centre, PatientId patient);

    record Identite(String nom, String prenom, String sexe, LocalDate dateNaissance, String numeroAssurance) {
    }
}
