package com.hemodialyse.backend.domain.referential.admin.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Saisie refusée : porte le détail champ par champ pour que le formulaire puisse l'afficher.
 */
public class ReferentialValidationException extends BusinessException {

    public static final String CODE = "REFERENTIAL_VALIDATION";

    private final transient List<ValidationIssue> issues;

    public ReferentialValidationException(List<ValidationIssue> issues) {
        super(CODE, issues.stream().map(ValidationIssue::message).collect(Collectors.joining(" ")));
        this.issues = List.copyOf(issues);
    }

    public List<ValidationIssue> getIssues() {
        return issues;
    }
}

