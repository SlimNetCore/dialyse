package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;

/**
 * Port sortant (DOC5g, ADR 0199) : les ouvertures de droit du centre telles que la liste des ouvertures de droit les imprime, avec le
 * patient qu'elles couvrent (même centre), triées par date de fin décroissante (le tri est celui de la base, comme l'ancienne requête du
 * gabarit). Lecture seule, bornée au centre ; un centre sans ouverture de droit donne une liste vide.
 */
public interface ListeDesAttestationsAImprimer {

    /** Une ouverture de droit de la liste imprimée (valeurs telles que stockées : une valeur absente reste nulle). */
    record AttestationImprimee(String codePatient, String nom, String prenom, String numeroAssurance, LocalDate dateDebut,
                               LocalDate dateFin) {
    }

    List<AttestationImprimee> duCentre(CentreId centre);
}
