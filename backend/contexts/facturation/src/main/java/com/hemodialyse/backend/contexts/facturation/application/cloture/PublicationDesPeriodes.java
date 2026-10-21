package com.hemodialyse.backend.contexts.facturation.application.cloture;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.PeriodeDeFacturationCloturee;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.PeriodeDeFacturationRouverte;

/**
 * Port sortant : publie les événements d'intégration de la clôture mensuelle, dans la transaction du cas d'usage (ADR 0013,
 * ADR 0037). Aucun consommateur de production pour l'instant.
 */
public interface PublicationDesPeriodes {

    void cloturee(PeriodeDeFacturationCloturee evenement);

    void rouverte(PeriodeDeFacturationRouverte evenement);
}
