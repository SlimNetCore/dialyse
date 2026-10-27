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

    /**
     * Place refusée par une règle de la planification lors de l'enregistrement d'une fiche : 422 avec le code stable
     * {@code PLACEMENT_<règle>} (même corps que le legacy). Levé par le langage publié des places, appelé par la fiche du patient.
     */
    @ExceptionHandler(com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacementDesPatients.Refus.class)
    ProblemDetail placementRefuse(com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacementDesPatients.Refus erreur) {
        return ErreursMetierDeLaPlanificationAdvice.probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle metier violee",
                erreur.getMessage(), erreur.code());
    }

    @ExceptionHandler(com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacementDesPatients.EcritureConcurrente.class)
    ProblemDetail placementConcurrent(
            com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacementDesPatients.EcritureConcurrente erreur) {
        return ErreursMetierDeLaPlanificationAdvice.probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(),
                "PLANIFICATION_ECRITURE_CONCURRENTE");
    }

    @ExceptionHandler(EcritureConcurrenteDesFiches.class)
    ProblemDetail ecritureConcurrente(EcritureConcurrenteDesFiches erreur) {
        return ErreursMetierDeLaPlanificationAdvice.probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(),
                "PLANIFICATION_ECRITURE_CONCURRENTE");
    }
}
