package com.hemodialyse.backend.contexts.gmao.application;

import java.util.function.Supplier;

/** Port sortant : frontière transactionnelle d'un use case (une transaction = un agrégat). */
public interface UniteDeTravail {
    <T> T executer(Supplier<T> action);
}
