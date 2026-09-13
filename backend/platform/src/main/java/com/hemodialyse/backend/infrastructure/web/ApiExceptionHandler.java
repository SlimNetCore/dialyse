package com.hemodialyse.backend.infrastructure.web;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.stock.exception.SeanceBilledStockModificationException;
import com.hemodialyse.backend.domain.stock.exception.SeanceStockExitDateImmutableException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Sans ce handler, le {@code @ExceptionHandler(Exception.class)} ci-dessous capterait les refus
     * d'accès levés depuis un contrôleur (ex. {@code CenterAccessGuard}) et les transformerait en 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Acces refuse");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "ACCESS_DENIED");
        return problem;
    }

    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusinessException(BusinessException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        problem.setTitle("Regle metier violee");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", ex.getCode());
        return problem;
    }

    @ExceptionHandler(SeanceStockExitDateImmutableException.class)
    ProblemDetail handleSeanceStockExitDateImmutable(SeanceStockExitDateImmutableException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        problem.setTitle("Modification interdite");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "SEANCE_STOCK_EXIT_DATE_IMMUTABLE");
        return problem;
    }

    @ExceptionHandler(SeanceBilledStockModificationException.class)
    ProblemDetail handleSeanceBilledStockImmutable(SeanceBilledStockModificationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        problem.setTitle("Modification interdite");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "SEANCE_BILLED_STOCK_EXIT_IMMUTABLE");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Requete invalide");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "BAD_REQUEST");
        return problem;
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleBusinessRule(IllegalStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        problem.setTitle("Regle metier violee");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "BUSINESS_RULE_VIOLATION");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation echouee");
        problem.setDetail(ex.getBody().getDetail());
        problem.setProperty("code", "VALIDATION_ERROR");
        return problem;
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail handleDataAccess(DataAccessException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Erreur technique");
        problem.setDetail(ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage());
        problem.setProperty("code", "DATA_ACCESS_ERROR");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Erreur interne");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "INTERNAL_ERROR");
        return problem;
    }
}
