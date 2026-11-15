package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant (DOC5h, ADR 0200) : l'ouverture de droit à imprimer avec le patient qu'elle couvre (même centre). Lecture seule, bornée au
 * centre : une ouverture de droit d'un autre centre, ou dont le patient n'est pas un patient du centre, n'est pas trouvée.
 */
public interface AttestationAImprimer {

    /** Une ouverture de droit à imprimer (valeurs telles que stockées : une valeur absente reste nulle). */
    record AttestationImprimable(String nom, String prenom, String sexe, LocalDate dateNaissance, String numeroAssurance,
                                 String adresse, String telephoneMobile, LocalDate dateDebut, LocalDate dateFin) {
    }

    /** L'ouverture de droit demandée par son identifiant. */
    Optional<AttestationImprimable> parIdentifiant(CentreId centre, UUID attestation);

    /** La dernière ouverture de droit du patient (date de fin la plus tardive, l'ordre étant celui de la base). */
    Optional<AttestationImprimable> laPlusRecenteDuPatient(CentreId centre, UUID patient);
}
