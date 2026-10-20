package com.hemodialyse.backend.contexts.facturation.application.validation;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FacturationValideeEvent;

/**
 * Port sortant : publie l'événement d'intégration de la validation. Appelé dans la transaction de la validation ; l'adapter le
 * livre aux consommateurs après le commit (ADR 0027 pour la Comptabilité).
 */
public interface PublicationDeLaValidation {

    void facturationValidee(FacturationValideeEvent evenement);
}
