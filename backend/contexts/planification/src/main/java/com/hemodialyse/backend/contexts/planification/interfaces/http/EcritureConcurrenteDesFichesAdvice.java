package com.hemodialyse.backend.contexts.planification.interfaces.http;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.FichesDesInfirmiers.EcritureConcurrenteDesFiches;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Rend en 409 {@code PLANIFICATION_ECRITURE_CONCURRENTE} l'écriture concurrente signalée par le langage publié des fiches, pour
 * toute route (en particulier l'administration des utilisateurs du legacy, qui appelle ce langage). Sans limite de contrôleur : le
 * type d'exception n'est levé que par {@code FichesDesInfirmiersPourLeLegacy}.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class EcritureConcurrenteDesFichesAdvice {

    @ExceptionHandler(EcritureConcurrenteDesFiches.class)
    ProblemDetail ecritureConcurrente(EcritureConcurrenteDesFiches erreur) {
        return ErreursMetierDeLaPlanificationAdvice.probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(),
                "PLANIFICATION_ECRITURE_CONCURRENTE");
    }
}
