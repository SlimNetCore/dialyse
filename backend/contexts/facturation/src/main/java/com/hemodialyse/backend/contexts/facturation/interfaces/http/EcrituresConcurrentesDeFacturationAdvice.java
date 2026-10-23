package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.EcritureConcurrenteDeFacturation;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Verrou non obtenu dans le délai de la transaction, ou interblocage (ADR 0043) : 409 {@code FACTURATION_ECRITURE_CONCURRENTE},
 * au format commun de la plateforme. Rien n'a été écrit, l'acte peut être relancé. Vaut pour tous les contrôleurs du contexte.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = EcrituresConcurrentesDeFacturationAdvice.class)
class EcrituresConcurrentesDeFacturationAdvice {

    @ExceptionHandler(EcritureConcurrenteDeFacturation.class)
    ProblemDetail ecritureConcurrente(EcritureConcurrenteDeFacturation erreur) {
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        probleme.setTitle("Ecriture concurrente");
        probleme.setDetail(erreur.getMessage());
        probleme.setProperty("code", "FACTURATION_ECRITURE_CONCURRENTE");
        return probleme;
    }
}

