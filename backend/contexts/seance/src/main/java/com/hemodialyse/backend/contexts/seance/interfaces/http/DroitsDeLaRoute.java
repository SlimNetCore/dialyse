package com.hemodialyse.backend.contexts.seance.interfaces.http;

import org.springframework.expression.ExpressionException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.core.Authentication;

/**
 * Évalue l'expression {@code @PreAuthorize} d'une route avec la racine d'expressions de Spring Security (même mécanisme que les routes
 * du Dossier médical), pour refuser en 403 un profil sans droit AVANT toute erreur de lecture du corps (403 avant 400, KB-SEA-04). Une
 * expression hors du jeu pris en charge échoue avec un message explicite : jamais de refus ni d'accord silencieux.
 */
final class DroitsDeLaRoute {

    private DroitsDeLaRoute() {
    }

    static boolean suffisants(String expression, Authentication authentification) {
        if (authentification == null) return false;
        SecurityExpressionRoot<Object> racine = new SecurityExpressionRoot<Object>(authentification) {
        };
        Boolean accord;
        try {
            accord = new SpelExpressionParser().parseExpression(expression).getValue(new StandardEvaluationContext(racine), Boolean.class);
        } catch (ExpressionException erreur) {
            throw new IllegalStateException("Expression @PreAuthorize non prise en charge par la garde 403-avant-400 : " + expression, erreur);
        }
        if (accord == null) throw new IllegalStateException("Expression @PreAuthorize sans résultat booléen : " + expression);
        return accord;
    }
}
