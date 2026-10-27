package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Personnel;

import java.time.LocalDate;

/**
 * Port sortant : le personnel du centre que la présence compare aux patients (infirmiers actifs, roulement des actifs, absences et
 * remplacements qui touchent la période) et le ratio de sécurité retenu (valeur illisible remplacée par le défaut, KB-PLN-47).
 * Lecture seule, bornée au centre.
 */
public interface PersonnelDeLaPresence {

    /**
     * @param du premier jour de la période (inclus)
     * @param au dernier jour de la période (inclus)
     */
    Personnel lire(CentreId centre, LocalDate du, LocalDate au);
}
