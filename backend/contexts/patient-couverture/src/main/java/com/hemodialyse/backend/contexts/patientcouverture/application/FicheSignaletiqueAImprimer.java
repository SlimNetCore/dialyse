package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant (DOC5i, ADR 0201) : la fiche signalétique d'un patient à imprimer. Lecture seule, bornée au centre : un patient d'un autre
 * centre n'est pas trouvé.
 */
public interface FicheSignaletiqueAImprimer {

    /** Les valeurs imprimées de la fiche, telles que stockées (une valeur absente reste nulle). */
    record FicheImprimable(String codePatient, String civilite, String nom, String prenom, String sexe, String groupeSanguin,
                           LocalDate dateNaissance, String lieuNaissance, LocalDate dateAdmission, Integer nombreEnfants,
                           String situationFamiliale, String profession, String adresse, String telPersonnel, String telMobile,
                           String telBureau, String email, String numeroAssurance, String etatPatient, String qualiteAssure,
                           String observation, Boolean sousKt, String assureNom, String assurePrenom) {
    }

    /** La fiche du patient du centre ; patient inconnu ou d'un autre centre : vide. */
    Optional<FicheImprimable> duPatient(CentreId centre, UUID patient);
}
