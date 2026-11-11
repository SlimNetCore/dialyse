package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Port publié (lecture) : <b>les fiches des patients d'un centre</b> (patientcouverture.v1, PC5). Toutes les lectures sont BORNÉES
 * au centre demandé : un patient d'un autre centre est absent du résultat. Aucune règle nouvelle : ce sont les lectures que les
 * autres contextes faisaient en SQL direct sur {@code patients}, à l'identique (mêmes filtres, mêmes colonnes).
 */
public interface FichesDesPatients {

    /** Un patient existe pour le centre s'il y est enregistré (Absences : contrôle d'une absence). */
    boolean existe(UUID centre, UUID patient);

    /**
     * Prénom et nom, tels que saisis (peuvent être nuls ou vides), des patients demandés qui sont du centre. Un patient inconnu ou
     * d'un autre centre est absent de la table rendue. Les consommateurs composent leur libellé (« nom prénom », « prénom nom »).
     */
    Map<UUID, NomDuPatient> noms(UUID centre, Collection<UUID> patients);

    /** Où en est le patient dans le centre : inconnu, suivi, ou parti (RG-PAT-032 : transféré, décédé, greffé, guéri). */
    Situation situation(UUID centre, UUID patient);

    /**
     * État civil et numéro d'assurance du patient du centre, tels que saisis (peuvent être nuls ou vides) : lu par l'export FHIR du
     * Dossier médical (DM8). Un patient inconnu ou d'un autre centre : vide.
     */
    java.util.Optional<IdentiteDuPatient> identite(UUID centre, UUID patient);

    /**
     * Carte d'identité du patient du centre, telle que saisie (champs nuls ou vides possibles) : code, état civil, groupe
     * sanguin, numéro d'assurance et téléphone mobile (Cahier de dialyse). Un patient inconnu ou d'un autre centre : vide.
     */
    java.util.Optional<CarteDuPatient> carte(UUID centre, UUID patient);

    /**
     * Le médecin traitant renseigné sur la fiche du patient du centre (identifiant tel que saisi, aucun contrôle d'existence du
     * médecin). Patient inconnu, d'un autre centre ou sans médecin traitant : vide.
     */
    java.util.Optional<UUID> medecinTraitant(UUID centre, UUID patient);

    /**
     * Le patient du centre portant ce code, par comparaison EXACTE (casse et espaces comptent : ni majuscules ni retrait appliqués).
     * Aucun ne correspond, ou celui d'un autre centre : vide. Si plusieurs patients portent le même code (aucune contrainte en base),
     * le premier par identifiant croissant (ordre hexadécimal de l'UUID).
     */
    java.util.Optional<UUID> parCode(UUID centre, String code);

    /** Idem {@link #parCode} pour le numéro d'assurance du patient (comparaison exacte, premier par identifiant croissant). */
    java.util.Optional<UUID> parNumeroDAssurance(UUID centre, String numero);

    /**
     * Les références de TOUS les patients du centre (sans filtre d'état), par identifiant croissant (ordre hexadécimal de l'UUID).
     * Valeurs telles que saisies (le code et le numéro de l'assuré peuvent être nuls).
     */
    java.util.List<ReferencesDuPatient> references(UUID centre);

    record ReferencesDuPatient(UUID patient, String code, String numeroAssurance, String numeroAssuranceDeLAssure) {
    }

    record CarteDuPatient(String code, String nom, String prenom, String sexe, java.time.LocalDate dateNaissance,
                          String groupeSanguin, String numeroAssurance, String telMobile) {
    }

    record NomDuPatient(String prenom, String nom) {
    }

    record IdentiteDuPatient(String nom, String prenom, String sexe, java.time.LocalDate dateNaissance, String numeroAssurance) {
    }

    enum Situation {
        /** Aucun patient de ce centre n'a cet identifiant. */
        INTROUVABLE,
        /** Le patient est suivi par le centre (y compris sans état renseigné, en séjour limité, en sommeil). */
        EN_SUIVI,
        /** L'état est une sortie : {@code TRANSFERE}, {@code DECEDE}, {@code GREFFE} ou {@code GUERRI}. */
        SORTI
    }
}
