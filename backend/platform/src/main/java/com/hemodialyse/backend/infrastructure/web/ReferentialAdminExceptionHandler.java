package com.hemodialyse.backend.infrastructure.web;

import com.hemodialyse.backend.domain.referential.admin.model.ReferentialValidationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Erreurs propres à l'administration des référentiels, traitées avant le gestionnaire générique
 * ({@link ApiExceptionHandler}) : détail champ par champ d'une saisie refusée, fichier d'import trop volumineux.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReferentialAdminExceptionHandler {

    @ExceptionHandler(ReferentialValidationException.class)
    ProblemDetail handleValidation(ReferentialValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        problem.setTitle("Saisie invalide");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", ex.getCode());
        problem.setProperty("issues", ex.getIssues().stream().map(issue -> {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("row", issue.row());
            view.put("field", issue.field());
            view.put("code", issue.code());
            view.put("message", issue.message());
            view.put("params", issue.params());
            return view;
        }).toList());
        return problem;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONTENT_TOO_LARGE);
        problem.setTitle("Fichier trop volumineux");
        problem.setDetail("Le fichier dépasse la taille maximale autorisée (5 Mo).");
        problem.setProperty("code", "IMPORT_FILE_TOO_LARGE");
        return problem;
    }
}

