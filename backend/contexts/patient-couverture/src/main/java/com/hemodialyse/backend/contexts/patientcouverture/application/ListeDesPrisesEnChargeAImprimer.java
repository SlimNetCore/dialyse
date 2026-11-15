package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;

/**
 * Port sortant (DOC5g, ADR 0199) : les prises en charge du centre telles que la liste des prises en charge les imprime, avec le patient
 * concerné (même centre), triées par date de fin de demande décroissante (le tri est celui de la base, comme l'ancienne requête du
 * gabarit). Lecture seule, bornée au centre ; un centre sans prise en charge donne une liste vide.
 */
public interface ListeDesPrisesEnChargeAImprimer {

    /** Une prise en charge de la liste imprimée (valeurs telles que stockées : une valeur absente reste nulle). */
    record PriseEnChargeImprimee(String codePatient, String nom, String prenom, String numeroAssurance, LocalDate dateDebutDemande,
                                 LocalDate dateFinDemande, String statut) {
    }

    List<PriseEnChargeImprimee> duCentre(CentreId centre);
}
