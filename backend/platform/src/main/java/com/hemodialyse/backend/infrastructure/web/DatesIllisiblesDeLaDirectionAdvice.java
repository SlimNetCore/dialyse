package com.hemodialyse.backend.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;

/**
 * KB-ABS-33, KB-STK-55 et KB-PLN-67 : {@code GET /api/v1/direction/absences?from=hier}, {@code /stock-groupes} et {@code /capacite}
 * répondent 400 {@code BAD_REQUEST} (c'était un 500). DM8 (KB-MED-103 et 105, ADR 0087) : mêmes 400 pour les indicateurs médicaux de la
 * Direction ({@code /direction/indicators}) et les statistiques médicales et exports du patient ({@code stats/medical},
 * {@code stats/export}, GET et POST {@code search}) : date, identifiant, paramètre absent ou corps illisible ; pour ces dernières le 403
 * du profil sans droit passe AVANT le 400 (la lecture du corps précède le contrôle {@code @PreAuthorize}). Limité à ces routes
 * (reconnues par leur chemin) : pour toute autre route une erreur de requête garde la réponse du gestionnaire commun
 * {@link ApiExceptionHandler}, auquel on délègue.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.hemodialyse.backend.infrastructure.web.rest")
class DatesIllisiblesDeLaDirectionAdvice {

    private static final java.util.Set<String> ROUTES = java.util.Set.of("/api/v1/direction/absences", "/api/v1/direction/stock-groupes",
            "/api/v1/direction/capacite", "/api/v1/direction/indicators");
    private static final java.util.List<String> FRAGMENTS_DES_STATISTIQUES = java.util.List.of("/stats/medical", "/stats/export");

    private final ApiExceptionHandler gestionnaireCommun;

    DatesIllisiblesDeLaDirectionAdvice(ApiExceptionHandler gestionnaireCommun) {
        this.gestionnaireCommun = gestionnaireCommun;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametreIllisible(MethodArgumentTypeMismatchException erreur, HttpServletRequest requete) {
        if (!estUneRouteReprise(requete)) {
            return gestionnaireCommun.handleUnexpected(erreur);
        }
        return reponse(erreur, requete, "Paramètre illisible : " + erreur.getName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail parametreAbsent(MissingServletRequestParameterException erreur, HttpServletRequest requete) {
        if (!estUneRouteDesStatistiques(requete)) {
            return gestionnaireCommun.handleUnexpected(erreur);
        }
        return reponse(erreur, requete, "Paramètre obligatoire absent : " + erreur.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail corpsIllisible(HttpMessageNotReadableException erreur, HttpServletRequest requete) {
        if (!estUneRouteDesStatistiques(requete)) {
            return gestionnaireCommun.handleUnexpected(erreur);
        }
        return reponse(erreur, requete, "Corps de la requête illisible");
    }

    private ProblemDetail reponse(Exception erreur, HttpServletRequest requete, String detail) {
        if (estUneRouteDesStatistiques(requete) && !droitsSuffisants(requete)) {
            ProblemDetail refus = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
            refus.setTitle("Acces refuse");
            refus.setDetail("Accès refusé");
            refus.setProperty("code", "ACCESS_DENIED");
            return refus;
        }
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        probleme.setTitle("Requete invalide");
        probleme.setDetail(detail);
        probleme.setProperty("code", "BAD_REQUEST");
        return probleme;
    }

    private static boolean estUneRouteDesStatistiques(HttpServletRequest requete) {
        String uri = requete.getRequestURI();
        return uri.contains("/api/v1/patients/") && FRAGMENTS_DES_STATISTIQUES.stream().anyMatch(uri::contains);
    }

    private static boolean estUneRouteReprise(HttpServletRequest requete) {
        return ROUTES.stream().anyMatch(requete.getRequestURI()::endsWith) || estUneRouteDesStatistiques(requete);
    }

    /** Évalue l'expression {@code @PreAuthorize} de la route avec la racine d'expressions de Spring Security (403 avant 400). */
    private static boolean droitsSuffisants(HttpServletRequest requete) {
        if (!(requete.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod methode)) return true;
        PreAuthorize garde = methode.getMethodAnnotation(PreAuthorize.class);
        if (garde == null) return true;
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null) return false;
        SecurityExpressionRoot<Object> racine = new SecurityExpressionRoot<Object>(authentification) {
        };
        Boolean accord = new SpelExpressionParser().parseExpression(garde.value()).getValue(new StandardEvaluationContext(racine), Boolean.class);
        return Boolean.TRUE.equals(accord);
    }
}
