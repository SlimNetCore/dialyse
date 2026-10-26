package com.hemodialyse.backend.contexts.stock.infrastructure.evenements;

import com.hemodialyse.backend.contexts.stock.application.peremptions.EvenementsDePeremption;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.LotProcheExpiration;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Publie {@link LotProcheExpiration} comme événement Spring ; le registre Spring Modulith (Outbox, ADR 0013) l'enregistre dans la
 * transaction de l'unité de travail pour chaque écouteur transactionnel, et le rejoue tant qu'il n'est pas livré.
 */
public final class EvenementsDePeremptionSpring implements EvenementsDePeremption {

    private final ApplicationEventPublisher evenements;

    public EvenementsDePeremptionSpring(ApplicationEventPublisher evenements) {
        this.evenements = evenements;
    }

    @Override
    public void publier(LotProcheExpiration evenement) {
        evenements.publishEvent(evenement);
    }
}
