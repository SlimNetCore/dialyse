package com.hemodialyse.backend.contexts.notifications.interfaces.http;

import com.hemodialyse.backend.contexts.notifications.application.AccesRefuse;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationInvalide;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Les refus du journal, au format commun de la plateforme ({@code ProblemDetail} + {@code code} stable) : 403 {@code ACCESS_DENIED} (centre),
 * 400 {@code BAD_REQUEST} (paramètre ou corps absent ou illisible, KB-NOT1-2), 400 {@code VALIDATION_ERROR} (identifiants absents du corps).
 * Aucun message ne reprend de nom de classe ni de détail technique.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = JournalDesNotificationsControleur.class)
class ErreursDuJournalAdvice {

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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametreIllisible(MethodArgumentTypeMismatchException erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Paramètre illisible : " + erreur.getName(), "BAD_REQUEST");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail parametreAbsent(MissingServletRequestParameterException erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Paramètre obligatoire absent : " + erreur.getParameterName(), "BAD_REQUEST");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail corpsIllisible() {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Corps de la requête illisible", "BAD_REQUEST");
    }

    @ExceptionHandler(NotificationInvalide.class)
    ProblemDetail notificationInvalide(NotificationInvalide erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Validation", erreur.getMessage(), "VALIDATION_ERROR");
    }
}
