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

    /**
     * LEC-PAT (ii), ARGENT (ADR 0177) : la fiche de facturation de ceux des patients demandés QUI SONT DU CENTRE (un patient d'un autre
     * centre ou inconnu est absent : la séance qui le référence n'est pas facturable), triés par nom puis prénom (collation de la base) ;
     * {@code rang} numérote les couples (nom, prénom) distincts dans cet ordre, à partir de 1. Valeurs telles que saisies (peuvent
     * être nulles) ; {@code etat} est l'état du patient (nul si non renseigné).
     */
    List<PatientAFacturer> aFacturerDuCentre(UUID centre, Collection<UUID> patients);

    /**
     * Le centre payeur de ceux des patients demandés qui sont du centre ET en ont un ; les autres (inconnu, d'un autre centre, sans
     * centre payeur) sont absents de la table. Lecture bornée au centre : la Séance en tire la caisse de chaque patient.
     */
    Map<UUID, UUID> centresPayeursDuCentre(UUID centre, Collection<UUID> patients);

    record PatientAFacturer(UUID patient, String code, String nom, String prenom, String etat, String numeroAssurance, UUID centrePayeur,
                            int rang) {
    }

    record EtatCivilDuPatient(UUID patient, String sexe, LocalDate dateNaissance) {
    }

    record PatientDuLot(UUID patient, String code, String nom, String prenom, int rang) {
    }
}
