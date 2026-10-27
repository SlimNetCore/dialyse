package com.hemodialyse.backend.contexts.planification.interfaces.http;

import com.hemodialyse.backend.contexts.planification.domain.PaginationInvalide;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Les entrées illisibles des routes du paramétrage de la planification, au format commun ({@code ProblemDetail} avec un
 * {@code code} stable) : 400 {@code BAD_REQUEST} pour un paramètre, un identifiant du chemin, un corps JSON, un jour, une
 * compétence ou un identifiant du corps illisibles et pour une pagination négative (KB-PLN-01, 02, 08 alignés : c'étaient des 500),
 * 415 {@code UNSUPPORTED_MEDIA_TYPE} pour un corps qui n'est pas du JSON. Limité aux contrôleurs de la planification.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {ParametresControleur.class, PreferencesControleur.class, InfirmiersControleur.class,
        RoulementControleur.class, AbsencesDesInfirmiersControleur.class, MonPlanningControleur.class, RemplacementsControleur.class,
        PresenceControleur.class})
class EntreesDeLaPlanificationAdvice {

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    ProblemDetail parametreManquant(org.springframework.web.bind.MissingServletRequestParameterException erreur) {
        return ErreursMetierDeLaPlanificationAdvice.probleme(HttpStatus.BAD_REQUEST, "Requete invalide",
                "Paramètre obligatoire absent : " + erreur.getParameterName(), "BAD_REQUEST");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametreIllisible(MethodArgumentTypeMismatchException erreur) {
        return ErreursMetierDeLaPlanificationAdvice.probleme(HttpStatus.BAD_REQUEST, "Requete invalide",
                "Paramètre illisible : " + erreur.getName(), "BAD_REQUEST");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail corpsIllisible(HttpMessageNotReadableException erreur) {
        return ErreursMetierDeLaPlanificationAdvice.probleme(HttpStatus.BAD_REQUEST, "Requete invalide",
                "Corps de la requête illisible", "BAD_REQUEST");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail typeDeContenuNonSupporte(HttpMediaTypeNotSupportedException erreur) {
        return ErreursMetierDeLaPlanificationAdvice.probleme(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Type de contenu non supporte",
                "Le corps de la requête doit être du JSON", "UNSUPPORTED_MEDIA_TYPE");
    }

    @ExceptionHandler(PaginationInvalide.class)
    ProblemDetail paginationInvalide(PaginationInvalide erreur) {
        return ErreursMetierDeLaPlanificationAdvice.probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(),
                "BAD_REQUEST");
    }
}
