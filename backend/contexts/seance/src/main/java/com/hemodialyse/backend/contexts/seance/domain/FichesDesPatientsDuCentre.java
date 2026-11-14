package com.hemodialyse.backend.contexts.seance.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port sortant : ce que la fiche du patient (propriété de Patient et couverture) dit aux lectures d'argent et de pilotage de la Séance
 * (LEC-PAT sous-slice ii, ADR 0177). Toutes les lectures sont BORNÉES au centre indiqué : un patient d'un autre centre est absent.
 * Le tri et la collation (nom puis prénom) sont ceux de Patient et couverture ; la Séance départage les homonymes par ses propres critères.
 */
public interface FichesDesPatientsDuCentre {

    /**
     * La fiche de facturation de ceux des patients demandés qui sont du centre, triée par nom puis prénom ; {@code rang} numérote les
     * couples (nom, prénom) distincts dans cet ordre (deux patients de même rang sont des homonymes).
     */
    List<PatientAFacturer> aFacturer(UUID centre, Collection<UUID> patients);

    /** Le centre payeur de ceux des patients demandés qui sont du centre et en ont un ; les autres sont absents. */
    Map<UUID, UUID> centresPayeurs(UUID centre, Collection<UUID> patients);

    /** Prénom et nom, tels que saisis (peuvent être nuls), de TOUS les patients du centre. */
    Map<UUID, NomDuPatient> noms(UUID centre);

    record PatientAFacturer(UUID patient, String code, String nom, String prenom, String etat, String numeroAssurance, UUID centrePayeur,
                            int rang) {
    }

    record NomDuPatient(String prenom, String nom) {
    }
}
