package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import org.springframework.expression.ExpressionException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.core.Authentication;

/**
 * Évalue l'expression {@code @PreAuthorize} d'une route avec la racine d'expressions de Spring Security (les mêmes {@code hasRole},
 * {@code hasAnyRole}, {@code hasAuthority}, {@code hasAnyAuthority}, {@code permitAll}, {@code isAuthenticated}, opérateurs
 * {@code and} / {@code or} / {@code !}), au lieu d'extraire les rôles par expression régulière. Une expression qui sort de ce
 * jeu (bean, argument de méthode, propriété inconnue) échoue avec un message explicite : jamais de refus ni d'accord silencieux.
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
