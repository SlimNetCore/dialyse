package com.hemodialyse.backend.contexts.documents.application;

import java.util.function.Supplier;

/** Port sortant : la frontière transactionnelle d'un cas d'usage (tout ou rien ; rejoint la transaction en cours s'il y en a une). */
public interface UniteDeTravail {

    <T> T executer(Supplier<T> action);
}
