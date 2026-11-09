package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant du domaine : les antécédents des patients (table {@code antecedents_medicaux}, inchangée, sans contrainte d'unicité ni
 * clé étrangère). Toujours cherchés par (identifiant, patient, centre). Ordre global des verrous : centre (Planification), fiches (cette
 * table), puis {@code app_user}, puis verrous consultatifs.
 */
public interface Antecedents {
    /**
     * Dit si un AUTRE antécédent ACTIF du patient porte déjà ce diagnostic (même système et même code, tous types confondus),
     * EN VERROUILLANT ce diagnostic jusqu'à la fin de la transaction. {@code sauf} : l'antécédent en cours de correction (peut être nul).
     * Lève {@link EcritureConcurrenteSurUnAntecedent} si le verrou n'est pas obtenu à temps.
     */
    boolean unDiagnosticActifExisteEnVerrouillant(CentreId centre, PatientId patient, CodeClinique diagnostic, UUID sauf);

    void ajouter(Antecedent antecedent);

    /** Enregistre la correction ou la résolution (tous les champs modifiables et la date de modification). */
    void enregistrerLaCorrection(Antecedent antecedent);

    /** Lecture avec verrou jusqu'à la fin de la transaction. Lève {@link EcritureConcurrenteSurUnAntecedent}. */
    Optional<Antecedent> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id);

    /** Supprime l'antécédent ; faux s'il n'existait pas (rien n'a été supprimé). */
    boolean supprimer(CentreId centre, PatientId patient, UUID id);

    /** Antécédents du patient, du début le plus récent au plus ancien (départage : création puis identifiant). */
    PageDeFiches<Antecedent> duPatient(CentreId centre, PatientId patient, int page, int taille);

    /** Tous les antécédents lisibles du patient, dans le même ordre (dossier de greffe, DM9). */
    java.util.List<Antecedent> tousDuPatient(CentreId centre, PatientId patient);
}
