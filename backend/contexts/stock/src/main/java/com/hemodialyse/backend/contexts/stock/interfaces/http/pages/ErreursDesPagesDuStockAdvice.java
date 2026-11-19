package com.hemodialyse.backend.contexts.stock.interfaces.http.pages;

import com.hemodialyse.backend.contexts.stock.application.pagination.PaginationInvalide;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Refus des listes paginées du Stock (contrôleurs du paquet {@code pages} seulement : les autres routes gardent leur
 * comportement) au format commun {@code ProblemDetail} avec un {@code code} stable : {@code PAGE_INVALIDE},
 * {@code TAILLE_INVALIDE}, {@code TRI_INVALIDE}, {@code FILTRE_INVALIDE}, {@code PARAMETRE_INVALIDE} (paramètre illisible ou
 * obligatoire absent).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = ErreursDesPagesDuStockAdvice.class)
class ErreursDesPagesDuStockAdvice {

    private static ProblemDetail requete(String detail, String code) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        probleme.setTitle("Requete invalide");
        probleme.setProperty("code", code);
        return probleme;
    }

    @ExceptionHandler(PaginationInvalide.class)
    ProblemDetail paginationInvalide(PaginationInvalide erreur) {
        return requete(erreur.getMessage(), erreur.code());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametreIllisible(MethodArgumentTypeMismatchException erreur) {
        return requete("Le paramètre « " + erreur.getName() + " » est illisible", PaginationInvalide.PARAMETRE_INVALIDE);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail parametreAbsent(MissingServletRequestParameterException erreur) {
        return requete("Le paramètre « " + erreur.getParameterName() + " » est obligatoire", PaginationInvalide.PARAMETRE_INVALIDE);
    }
}
