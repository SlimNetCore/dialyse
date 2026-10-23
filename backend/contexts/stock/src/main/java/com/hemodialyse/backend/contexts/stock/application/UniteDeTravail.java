package com.hemodialyse.backend.contexts.stock.application;

import java.util.function.Supplier;

/**
 * Port sortant : exécute un cas d'usage dans une seule transaction (tout ou rien).
 */
public interface UniteDeTravail {

    <T> T executer(Supplier<T> travail);
}

