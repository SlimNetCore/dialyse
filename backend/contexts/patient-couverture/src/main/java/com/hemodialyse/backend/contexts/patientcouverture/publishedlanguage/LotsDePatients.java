package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port publié (lecture) : <b>des lots de patients</b> demandés par identifiant (patientcouverture.v1, LEC-PAT sous-slice i, ADR 0176).
 * La Séance détient les séances ; elle demande à Patient et couverture ce que la fiche dit des patients de ces séances.
 * <p>
 * Les deux lectures par lot ({@link #etatCivil}, {@link #parNomPuisPrenom}) ne sont PAS bornées au centre : une séance d'un centre peut
 * référencer un patient d'un autre centre et le legacy le rendait (KB-SEA-61, KB-SEA-64, reproduit, à confirmer métiers/DPO avant le
 * 2026-12-15). Le tri et la collation (nom puis prénom) sont ceux de la base de Patient et couverture.
 */
public interface LotsDePatients {

    /** Prénom et nom, tels que saisis (peuvent être nuls), de TOUS les patients du centre, sans ordre garanti. */
    Map<UUID, FichesDesPatients.NomDuPatient> identitesDuCentre(UUID centre);

    /**
     * Sexe et naissance, tels que saisis (peuvent être nuls), de ceux des patients demandés qui existent, un par patient, dans l'ordre
     * de la demande ; un identifiant inconnu est absent. Aucune borne de centre.
     */
    List<EtatCivilDuPatient> etatCivil(Collection<UUID> patients);

    /**
     * Code, nom et prénom, tels que saisis (peuvent être nuls), de ceux des patients demandés qui existent, triés par nom puis prénom
     * (collation de la base). {@code rang} numérote les couples (nom, prénom) distincts dans cet ordre, à partir de 1 : deux patients
     * de même rang sont des homonymes, que le consommateur départage par ses propres critères. Aucune borne de centre.
     */
    List<PatientDuLot> parNomPuisPrenom(Collection<UUID> patients);

    record EtatCivilDuPatient(UUID patient, String sexe, LocalDate dateNaissance) {
    }

    record PatientDuLot(UUID patient, String code, String nom, String prenom, int rang) {
    }
}
