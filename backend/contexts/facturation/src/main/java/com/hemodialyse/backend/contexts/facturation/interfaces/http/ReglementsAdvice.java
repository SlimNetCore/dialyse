package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.reglement.pagination.PaginationInvalide;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.RemboursementSuperieurAuTotalRegle;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Le refus propre aux règlements, au format commun de la plateforme ({@code ProblemDetail} avec un {@code code}) : 422
 * {@code REMBOURSEMENT_SUPERIEUR_AU_TOTAL_REGLE} (KB-FAC-43). Limité à {@link ReglementControleur} ; les autres erreurs suivent la
 * gestion commune (400 pour les arguments illisibles, 500 pour les erreurs de protocole : KB-FAC-44 reproduit).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ReglementControleur.class)
class ReglementsAdvice {

    @ExceptionHandler(RemboursementSuperieurAuTotalRegle.class)
    ProblemDetail remboursementTropEleve(RemboursementSuperieurAuTotalRegle erreur) {
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        probleme.setTitle("Regle metier violee");
        probleme.setDetail(erreur.getMessage());
        probleme.setProperty("code", "REMBOURSEMENT_SUPERIEUR_AU_TOTAL_REGLE");
        return probleme;
    }

    /**
     * Refus de {@code GET /reglements/page} seulement (exception levée par cette route) : 400 {@code Requete invalide} avec un
     * {@code code} stable du contrat {@code pagination-serveur}. Les autres routes gardent leurs refus historiques.
     */
    @ExceptionHandler(PaginationInvalide.class)
    ProblemDetail paginationInvalide(PaginationInvalide erreur) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, erreur.getMessage());
        probleme.setTitle("Requete invalide");
        probleme.setProperty("code", erreur.code());
        return probleme;
    }
}
