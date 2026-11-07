package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Provenance d'une observation biologique. Seule la saisie directe est produite par ce contexte ; DERIVEE_BILAN et IMPORT viennent de la
 * base (aucune projection du bilan vers les observations n'existe : « réservé à une phase ultérieure », KB-MED-84, rien inventé).
 */
public enum SourceDObservation {
    SAISIE_DIRECTE, DERIVEE_BILAN, IMPORT
}
