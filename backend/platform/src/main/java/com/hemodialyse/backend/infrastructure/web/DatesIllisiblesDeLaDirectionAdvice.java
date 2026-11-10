package com.hemodialyse.backend.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
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
 * <p>
 * RF1c (KB-RF1-01, ADR 0123) : mêmes 400 {@code BAD_REQUEST} pour l'administration des seuls TRANSPORTEURS
 * ({@code /api/v1/admin/referentials/transporteurs/**} : corps absent ou illisible, {@code centerId}, identifiant ou {@code page}
 * illisibles) ; le 403 du profil sans droit passe AVANT le 400.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.hemodialyse.backend.infrastructure.web.rest")
class DatesIllisiblesDeLaDirectionAdvice {

    private static final java.util.Set<String> ROUTES = java.util.Set.of("/api/v1/direction/absences", "/api/v1/direction/stock-groupes",
            "/api/v1/direction/capacite", "/api/v1/direction/indicators", "/api/v1/direction/overview", "/api/v1/direction/breakdown",
            "/api/v1/direction/report", "/api/v1/direction/gmao", "/api/v1/direction/alerts/history");
    /** KB-DIR-02 (lot L6) : les routes ajoutées renvoient le 403 du profil sans droit AVANT le 400 (les quatre routes d'origine gardent leur 400). */
    private static final java.util.Set<String> ROUTES_AVEC_DROITS_A_VERIFIER = java.util.Set.of("/api/v1/direction/indicators",
            "/api/v1/direction/overview", "/api/v1/direction/breakdown", "/api/v1/direction/report", "/api/v1/direction/gmao",
            "/api/v1/direction/alerts/history");
    private static final java.util.regex.Pattern TRANSPORTEURS_ADMIN =
            java.util.regex.Pattern.compile("/api/v1/admin/referentials/transporteurs(/|$)");
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
        if (!estUneRouteDesStatistiques(requete) && !estUneRouteDesTransporteursDeLAdministration(requete)) {
            return gestionnaireCommun.handleUnexpected(erreur);
        }
        return reponse(erreur, requete, "Paramètre obligatoire absent : " + erreur.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail corpsIllisible(HttpMessageNotReadableException erreur, HttpServletRequest requete) {
        if (!estUneRouteDesStatistiques(requete) && !estUneRouteDesTransporteursDeLAdministration(requete)) {
            return gestionnaireCommun.handleUnexpected(erreur);
        }
        return reponse(erreur, requete, "Corps de la requête illisible");
    }

    private ProblemDetail reponse(Exception erreur, HttpServletRequest requete, String detail) {
        if (estUneRouteAvecDroitsAVerifier(requete) && !droitsSuffisants(requete)) {
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

    /**
     * KB-RF1-01 (RF1c, ADR 0123) : l'administration des TRANSPORTEURS ({@code /api/v1/admin/referentials/transporteurs} et ses
     * sous-routes). Limitée à ce référentiel : les huit autres gardent leur 500 (résiduel RF1-R4).
     */
    private static boolean estUneRouteDesTransporteursDeLAdministration(HttpServletRequest requete) {
        return TRANSPORTEURS_ADMIN.matcher(requete.getRequestURI()).find();
    }

    /** Les statistiques médicales et les indicateurs médicaux de la Direction : le 403 passe avant le 400 (les autres routes gardent leur 400). */
    private static boolean estUneRouteAvecDroitsAVerifier(HttpServletRequest requete) {
        return estUneRouteDesStatistiques(requete) || estUneRouteDesTransporteursDeLAdministration(requete)
                || ROUTES_AVEC_DROITS_A_VERIFIER.stream().anyMatch(requete.getRequestURI()::endsWith);
    }

    private static boolean estUneRouteReprise(HttpServletRequest requete) {
        return ROUTES.stream().anyMatch(requete.getRequestURI()::endsWith) || estUneRouteDesStatistiques(requete)
                || estUneRouteDesTransporteursDeLAdministration(requete);
    }

    /** Évalue l'expression {@code @PreAuthorize} de la route avec la racine d'expressions de Spring Security (403 avant 400). */
    private static boolean droitsSuffisants(HttpServletRequest requete) {
        if (!(requete.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod methode)) return true;
        PreAuthorize garde = methode.getMethodAnnotation(PreAuthorize.class);
        if (garde == null) garde = org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(methode.getBeanType(), PreAuthorize.class);
        if (garde == null) return true;
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null) return false;
        SecurityExpressionRoot<Object> racine = new SecurityExpressionRoot<Object>(authentification) {
        };
        Boolean accord = new SpelExpressionParser().parseExpression(garde.value()).getValue(new StandardEvaluationContext(racine), Boolean.class);
        return Boolean.TRUE.equals(accord);
    }

    /**
     * KB-DIR-02 (lot L6, ADR 0119) : une méthode non supportée sur l'API de la Direction ({@code /api/v1/direction/**}) répond 405
     * {@code METHOD_NOT_ALLOWED} avec l'en-tête {@code Allow} (c'était un 500 du gestionnaire commun), sans le message technique de Spring.
     * Limité à ce préfixe : ailleurs la réponse reste celle du gestionnaire commun, auquel on délègue. Classe imbriquée et SANS restriction
     * de paquetage : l'erreur est levée avant le choix d'un contrôleur, donc l'advice ci-dessus (limité aux contrôleurs) ne la reçoit pas.
     */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @RestControllerAdvice
    static class MethodesNonSupporteesDeLaDirectionAdvice {

        private static final String PREFIXE = "/api/v1/direction/";

        private final ApiExceptionHandler gestionnaireCommun;

        MethodesNonSupporteesDeLaDirectionAdvice(ApiExceptionHandler gestionnaireCommun) {
            this.gestionnaireCommun = gestionnaireCommun;
        }

        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        ResponseEntity<ProblemDetail> methodeNonSupportee(HttpRequestMethodNotSupportedException erreur, HttpServletRequest requete) {
            if (!requete.getRequestURI().startsWith(PREFIXE)) {
                ProblemDetail commun = gestionnaireCommun.handleUnexpected(erreur);
                return ResponseEntity.status(commun.getStatus()).body(commun);
            }
            ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
            probleme.setTitle("Methode non supportee");
            probleme.setDetail("Méthode non supportée sur cette route");
            probleme.setProperty("code", "METHOD_NOT_ALLOWED");
            ResponseEntity.BodyBuilder reponse = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
            if (erreur.getSupportedHttpMethods() != null && !erreur.getSupportedHttpMethods().isEmpty()) {
                reponse.allow(erreur.getSupportedHttpMethods().toArray(new HttpMethod[0]));
            }
            return reponse.body(probleme);
        }
    }
}
