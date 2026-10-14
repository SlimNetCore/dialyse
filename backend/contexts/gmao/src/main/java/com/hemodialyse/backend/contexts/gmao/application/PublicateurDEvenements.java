package com.hemodialyse.backend.contexts.gmao.application;

import java.util.Collection;

/**
 * Port sortant : publie les événements levés par un agrégat. L'adaptateur les enregistre dans la transaction courante
 * (outbox Spring Modulith, ADR 0013) : ils ne sont livrés qu'après commit, et jamais perdus.
 */
public interface PublicateurDEvenements {

    void publier(Collection<?> evenements);
}
