package com.hemodialyse.backend.contexts.absencespatients.infrastructure.messaging;

import com.hemodialyse.backend.contexts.absencespatients.application.PublicationDesMoisDAbsences;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.MoisDAbsencesCloture;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.MoisDAbsencesRouvert;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Publie les événements de la clôture mensuelle des absences par le bus d'événements de l'application, dans la transaction du cas
 * d'usage : les écouteurs transactionnels (Spring Modulith, Outbox, ADR 0013) les reçoivent après le commit, et jamais si la
 * clôture ou la réouverture est annulée. Sans écouteur, rien n'est laissé dans le registre des publications (comme la Facturation).
 */
public class PublicationDesMoisDAbsencesSpring implements PublicationDesMoisDAbsences {

    private final ApplicationEventPublisher publisher;

    public PublicationDesMoisDAbsencesSpring(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void cloture(MoisDAbsencesCloture evenement) {
        publisher.publishEvent(evenement);
    }

    @Override
    public void rouvert(MoisDAbsencesRouvert evenement) {
        publisher.publishEvent(evenement);
    }
}
