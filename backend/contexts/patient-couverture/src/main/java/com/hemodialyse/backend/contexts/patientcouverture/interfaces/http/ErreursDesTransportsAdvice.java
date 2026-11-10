package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Les refus des listes de choix des transports, au format commun de la plateforme. Un identifiant de centre illisible
 * ({@code IllegalArgumentException} de la conversion) répond 400 {@code BAD_REQUEST} avec le message de la conversion, comme quand
 * ces routes étaient servies par l'Organisation (RF1 : réponse inchangée). Un paramètre absent reste une erreur 500 (comportement
 * conservé, KB-RF1-01). Limité à ce contrôleur.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = TransportsControleur.class)
class ErreursDesTransportsAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail identifiantIllisible(IllegalArgumentException erreur) {
        return ErreursDeLaFicheAdvice.probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }
}
