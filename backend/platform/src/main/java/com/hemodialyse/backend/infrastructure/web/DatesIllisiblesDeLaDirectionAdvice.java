package com.hemodialyse.backend.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * KB-ABS-33 : {@code GET /api/v1/direction/absences?from=hier} répond 400 {@code BAD_REQUEST} (c'était un 500). Limité à cette seule
 * route (reconnue par son chemin) : pour toute autre route un paramètre illisible garde la réponse du gestionnaire commun
 * {@link ApiExceptionHandler}, auquel on délègue.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.hemodialyse.backend.infrastructure.web.rest")
class DatesIllisiblesDeLaDirectionAdvice {

    private static final String ROUTE = "/api/v1/direction/absences";

    private final ApiExceptionHandler gestionnaireCommun;

    DatesIllisiblesDeLaDirectionAdvice(ApiExceptionHandler gestionnaireCommun) {
        this.gestionnaireCommun = gestionnaireCommun;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametreIllisible(MethodArgumentTypeMismatchException erreur, HttpServletRequest requete) {
        if (!requete.getRequestURI().endsWith(ROUTE)) {
            return gestionnaireCommun.handleUnexpected(erreur);
        }
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        probleme.setTitle("Requete invalide");
        probleme.setDetail("Paramètre illisible : " + erreur.getName());
        probleme.setProperty("code", "BAD_REQUEST");
        return probleme;
    }
}
