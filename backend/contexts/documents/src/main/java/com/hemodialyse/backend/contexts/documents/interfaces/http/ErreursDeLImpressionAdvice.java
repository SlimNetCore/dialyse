package com.hemodialyse.backend.contexts.documents.interfaces.http;

import com.hemodialyse.backend.contexts.documents.application.AccesRefuse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Les refus des routes d'impression, au format commun de la plateforme ({@code ProblemDetail} + {@code code} stable) : 403
 * {@code ACCESS_DENIED} (centre, KB-DOC1-1) et 400 {@code BAD_REQUEST} (corps absent ou illisible, identifiant de modèle illisible,
 * KB-DOC1-5 c). Les refus de profil de la plateforme (ordonnance, synthèse de facturation) restent traités par son gestionnaire commun.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ImpressionControleur.class)
class ErreursDeLImpressionAdvice {

    private static ProblemDetail probleme(HttpStatus statut, String titre, String detail, String code) {
        ProblemDetail probleme = ProblemDetail.forStatus(statut);
        probleme.setTitle(titre);
        probleme.setDetail(detail);
        probleme.setProperty("code", code);
        return probleme;
    }

    @ExceptionHandler(AccesRefuse.class)
    ProblemDetail accesRefuse(AccesRefuse erreur) {
        return probleme(HttpStatus.FORBIDDEN, "Acces refuse", erreur.getMessage(), "ACCESS_DENIED");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail corpsIllisible() {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Corps de la requête illisible", "BAD_REQUEST");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametreIllisible(MethodArgumentTypeMismatchException erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Paramètre illisible : " + erreur.getName(), "BAD_REQUEST");
    }
}
