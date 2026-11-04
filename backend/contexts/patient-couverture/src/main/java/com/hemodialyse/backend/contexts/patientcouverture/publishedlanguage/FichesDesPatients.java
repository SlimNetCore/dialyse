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

    record NomDuPatient(String prenom, String nom) {
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
