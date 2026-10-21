package com.hemodialyse.backend.contexts.facturation.application.reglement;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FactureReglementRecordedEvent;

/**
 * Port sortant : publie l'événement d'intégration d'un règlement enregistré. Appelé dans la transaction de l'enregistrement ;
 * l'adapter le livre aux consommateurs (Comptabilité) après le commit.
 */
public interface PublicationDesReglements {

    void reglementEnregistre(FactureReglementRecordedEvent evenement);
}
