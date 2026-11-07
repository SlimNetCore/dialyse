package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Ce que la Séance lit de la fiche du patient (tenue par Patient et couverture) : identité, repères de la place et générateur
 * déclaré. Valeurs telles que saisies (nulles ou vides possibles). Le générateur est ensuite précisé par {@link GenerateursDesPatients}.
 */
public record FicheDuPatient(PatientId id, String code, String nom, String prenom, String sexe, LocalDate dateDeNaissance,
                             String numeroDAssurance, String groupeSanguin, String telephoneMobile, UUID medecinTraitant,
                             UUID salle, UUID position, UUID generateur, String generateurNom, String generateurMarque,
                             String generateurEtat) {

    /** La même fiche, générateur précisé par l'équipement de la GMAO. */
    public FicheDuPatient avecLeGenerateur(GenerateursDesPatients.Generateur equipement) {
        return new FicheDuPatient(id, code, nom, prenom, sexe, dateDeNaissance, numeroDAssurance, groupeSanguin, telephoneMobile,
                medecinTraitant, salle, position, equipement.id(), equipement.code(), equipement.fabricant(), equipement.statut());
    }
}
