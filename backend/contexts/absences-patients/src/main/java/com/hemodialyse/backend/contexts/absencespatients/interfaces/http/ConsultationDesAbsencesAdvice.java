package com.hemodialyse.backend.contexts.absencespatients.interfaces.http;

import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceIntrouvable;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceNonModifiable;
import com.hemodialyse.backend.contexts.absencespatients.domain.PaginationInvalide;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Les refus de la consultation du suivi, au format commun de la plateforme ({@code ProblemDetail} avec un {@code code}
 * stable) : 400 {@code BAD_REQUEST} pour un paramètre illisible ou une pagination négative (KB-ABS-01, 02 corrigés), 404
 * {@code ABSENCE_INTROUVABLE} (KB-ABS-03 corrigé), 422 {@code ABSENCE_NON_MODIFIABLE} pour les séances de rattrapage d'une
 * absence rattrapée ou annulée (KB-ABS-04 corrigé). Limité au contrôleur de consultation : les écritures, encore au legacy,
 * gardent leurs codes.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AbsencesControleur.class)
class ConsultationDesAbsencesAdvice {

    private static ProblemDetail probleme(HttpStatus statut, String titre, String detail, String code) {
        ProblemDetail probleme = ProblemDetail.forStatus(statut);
        probleme.setTitle(titre);
        probleme.setDetail(detail);
        probleme.setProperty("code", code);
        return probleme;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametreIllisible(MethodArgumentTypeMismatchException erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Paramètre illisible : " + erreur.getName(), "BAD_REQUEST");
    }

    @ExceptionHandler(PaginationInvalide.class)
    ProblemDetail paginationInvalide(PaginationInvalide erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(AbsenceIntrouvable.class)
    ProblemDetail absenceIntrouvable(AbsenceIntrouvable erreur) {
        return probleme(HttpStatus.NOT_FOUND, "Absence introuvable", erreur.getMessage(), "ABSENCE_INTROUVABLE");
    }

    @ExceptionHandler(AbsenceNonModifiable.class)
    ProblemDetail absenceNonModifiable(AbsenceNonModifiable erreur) {
        return probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle metier violee", erreur.getMessage(), "ABSENCE_NON_MODIFIABLE");
    }
}
