package com.hemodialyse.backend.contexts.stock.interfaces.http;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Paramètre illisible d'une lecture du Stock (identifiant, nombre) : 400 {@code BAD_REQUEST} au lieu d'un 500 (KB-STK-24
 * corrigé). Limité au contrôleur des lectures : les autres routes du Stock gardent leur comportement (KB-STK-11).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = LecturesDuStockControleur.class)
class ErreursDeLectureDuStockAdvice {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametreIllisible(MethodArgumentTypeMismatchException erreur) {
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        probleme.setTitle("Requete invalide");
        probleme.setDetail("Le paramètre « " + erreur.getName() + " » est illisible");
        probleme.setProperty("code", "BAD_REQUEST");
        return probleme;
    }
}

