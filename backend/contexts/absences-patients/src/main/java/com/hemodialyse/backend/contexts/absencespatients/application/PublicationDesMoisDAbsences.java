package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.MoisDAbsencesCloture;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.MoisDAbsencesRouvert;

/**
 * Port sortant : publie les événements d'intégration de la clôture mensuelle des absences, dans la transaction du cas d'usage
 * (ADR 0013, ADR 0042). Aucun consommateur de production pour l'instant.
 */
public interface PublicationDesMoisDAbsences {

    void cloture(MoisDAbsencesCloture evenement);

    void rouvert(MoisDAbsencesRouvert evenement);
}
