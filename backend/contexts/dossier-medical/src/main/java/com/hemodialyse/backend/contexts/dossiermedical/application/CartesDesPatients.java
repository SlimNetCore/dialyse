package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/** Port sortant : la carte du patient (code, nom, prénom, groupe sanguin déclaré) tenue par Patient et couverture, lue pour l'en-tête du dossier de greffe imprimé. */
public interface CartesDesPatients {

    /** La carte du patient dans CE centre ; inconnu ou d'un autre centre : vide. Valeurs telles que saisies (nulles possibles). */
    Optional<Carte> duPatient(CentreId centre, UUID patient);

    record Carte(String code, String nom, String prenom, String groupeSanguin) {
    }
}
