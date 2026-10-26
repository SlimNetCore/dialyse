package com.hemodialyse.backend.contexts.stock.application.peremptions;

import com.hemodialyse.backend.contexts.stock.publishedlanguage.LotProcheExpiration;

/**
 * Port sortant : publie l'événement d'intégration {@link LotProcheExpiration} par l'Outbox. À appeler dans une unité de travail
 * (le registre n'enregistre que les publications faites dans une transaction).
 */
public interface EvenementsDePeremption {

    void publier(LotProcheExpiration evenement);
}
