package com.hemodialyse.backend.contexts.facturation.application.simulation;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.seance.MotifDAbsence;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port sortant : les absences de patients, tenues par le module Absences (hors Facturation, ACL tant qu'il n'est pas un
 * contexte). L'exclusion d'une séance déclare une absence avec son motif, la réinclusion la retire. Les règles du module
 * Absences s'appliquent : une déclaration refusée (délai de 60 jours, date future, période déjà facturée, absence déjà
 * déclarée à cette date) fait échouer l'exclusion (422).
 */
public interface AbsencesDesPatients {

    /**
     * Déclare l'absence du patient à la date de sa séance et rend son identité.
     *
     * @param auteur l'utilisateur de la session, tracé par l'absence
     */
    UUID declarer(CentreId centre, UUID patient, LocalDate dateDeSeance, MotifDAbsence motif, String commentaire, UUID auteur);

    /**
     * Retire l'absence déclarée par une exclusion (sans effet si elle n'existe plus).
     */
    void retirer(CentreId centre, UUID absence);
}
