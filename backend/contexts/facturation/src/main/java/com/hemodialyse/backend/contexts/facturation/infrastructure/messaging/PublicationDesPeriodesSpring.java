package com.hemodialyse.backend.contexts.facturation.infrastructure.messaging;

import com.hemodialyse.backend.contexts.facturation.application.cloture.PublicationDesPeriodes;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.PeriodeDeFacturationCloturee;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.PeriodeDeFacturationRouverte;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Publie les événements de la clôture mensuelle par le bus d'événements de l'application, dans la transaction du cas d'usage : les
 * écouteurs transactionnels les reçoivent après le commit, et jamais si la clôture ou la réouverture est annulée.
 */
public class PublicationDesPeriodesSpring implements PublicationDesPeriodes {

    private final ApplicationEventPublisher publisher;

    public PublicationDesPeriodesSpring(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void cloturee(PeriodeDeFacturationCloturee evenement) {
        publisher.publishEvent(evenement);
    }

    @Override
    public void rouverte(PeriodeDeFacturationRouverte evenement) {
        publisher.publishEvent(evenement);
    }
}
