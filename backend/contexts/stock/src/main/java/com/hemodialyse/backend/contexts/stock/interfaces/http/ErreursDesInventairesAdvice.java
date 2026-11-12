package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.application.inventaire.FeuilleInvalide;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Requêtes inutilisables sur le PV et la feuille de comptage (PL7, KB-PL7-1 et 4, décisions du 2026-11-12) : 400 au lieu de 500,
 * sans message technique. Limité au contrôleur des inventaires.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = InventairesControleur.class)
class ErreursDesInventairesAdvice {

    private static ProblemDetail requeteInvalide(String detail, String code) {
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        probleme.setTitle("Requete invalide");
        probleme.setDetail(detail);
        probleme.setProperty("code", code);
        return probleme;
    }

    @ExceptionHandler(IdentifiantIllisible.class)
    ProblemDetail parametreIllisible() {
        return requeteInvalide("Requête illisible", "BAD_REQUEST");
    }

    @ExceptionHandler(FeuilleInvalide.class)
    ProblemDetail feuilleInvalide(FeuilleInvalide erreur) {
        return requeteInvalide(erreur.getMessage(), erreur.code());
    }
}
