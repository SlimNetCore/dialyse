package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.domain.groupes.GroupeDArticlesInvalide;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ArticleIntrouvable;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Refus du référentiel du Stock, au format commun de la plateforme ({@code ProblemDetail} avec un {@code code} stable) : 422
 * pour une règle violée (codes du legacy conservés), 422 {@code ARTICLE_INTROUVABLE} (KB-STK-07 reproduit, décision utilisateur
 * du 2026-10-23), 400 pour une valeur inutilisable (KB-STK-06, 08 corrigés). Limité aux contrôleurs du contexte.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = ErreursDuReferentielAdvice.class)
class ErreursDuReferentielAdvice {

    private static ProblemDetail probleme(HttpStatus statut, String titre, String detail, String code) {
        ProblemDetail probleme = ProblemDetail.forStatus(statut);
        probleme.setTitle(titre);
        probleme.setDetail(detail);
        probleme.setProperty("code", code);
        return probleme;
    }

    @ExceptionHandler(RefusDuReferentiel.class)
    ProblemDetail regleViolee(RefusDuReferentiel erreur) {
        return probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle metier violee", erreur.getMessage(), erreur.code());
    }

    @ExceptionHandler(ArticleIntrouvable.class)
    ProblemDetail introuvable(ArticleIntrouvable erreur) {
        return probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle metier violee", erreur.getMessage(), "ARTICLE_INTROUVABLE");
    }

    @ExceptionHandler(ValeurInvalide.class)
    ProblemDetail valeurInvalide(ValeurInvalide erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), erreur.code());
    }

    /**
     * Composition de groupe inutilisable (slice S2) : 400 {@code BAD_REQUEST}, comme le legacy.
     */
    @ExceptionHandler(GroupeDArticlesInvalide.class)
    ProblemDetail groupeInvalide(GroupeDArticlesInvalide erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }
}
