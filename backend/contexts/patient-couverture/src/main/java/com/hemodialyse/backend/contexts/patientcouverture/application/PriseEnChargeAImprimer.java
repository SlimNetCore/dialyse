package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant (DOC5h, ADR 0200) : la prise en charge à imprimer avec le patient qu'elle couvre (même centre). Lecture seule, bornée au
 * centre : une prise en charge d'un autre centre, ou dont le patient n'est pas un patient du centre, n'est pas trouvée.
 */
public interface PriseEnChargeAImprimer {

    /** Une prise en charge à imprimer (valeurs telles que stockées : une valeur absente reste nulle). */
    record PriseEnChargeImprimable(String nom, String prenom, String numeroAssurance, LocalDate dateNaissance,
                                   LocalDate dateDebutDemande, LocalDate dateFinDemande, String statut, LocalDate dateDebutEffectif,
                                   LocalDate dateFinEffectif) {
    }

    /** La prise en charge demandée par son identifiant. */
    Optional<PriseEnChargeImprimable> parIdentifiant(CentreId centre, UUID priseEnCharge);

    /** La dernière prise en charge du patient (date de fin de demande la plus tardive, l'ordre étant celui de la base). */
    Optional<PriseEnChargeImprimable> laPlusRecenteDuPatient(CentreId centre, UUID patient);
}
