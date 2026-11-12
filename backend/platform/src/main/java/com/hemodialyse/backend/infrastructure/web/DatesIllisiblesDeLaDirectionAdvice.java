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
 * {@code stats/export}, GET et POST {@code search}) : date, identifiant, paramètre absent ou corps illisible ; le 403 du profil sans droit
 * passe AVANT le 400 (la lecture du corps précède le contrôle {@code @PreAuthorize}). PL4 (ADR 0139) : les routes des statistiques du patient
 * sont servies par {@code contexts/dossier-medical}, dont l'advice {@code ErreursDesStatistiquesDuPatientAdvice} garde les mêmes messages ;
 * il ne reste ici que la Direction et l'administration générique. Limité à ces routes
 * (reconnues par leur chemin) : pour toute autre route une erreur de requête garde la réponse du gestionnaire commun
 * {@link ApiExceptionHandler}, auquel on délègue.
 * <p>
 * RF1c (KB-RF1-01, ADR 0123) : mêmes 400 {@code BAD_REQUEST} pour l'administration des seuls TRANSPORTEURS
 * ({@code /api/v1/admin/referentials/transporteurs/**} : corps absent ou illisible, {@code centerId}, identifiant ou {@code page}
 * illisibles) ; le 403 du profil sans droit passe AVANT le 400.
 * <p>
 * RF2c (KB-RF2-01, ADR 0125) : étendu aux FORFAITS ({@code /api/v1/admin/referentials/forfaits/**}), mêmes règles.
 * <p>
 * RF3d (KB-RF3-01 à 03, ADR 0129) : étendu à TOUTE l'administration générique ({@code /api/v1/admin/referentials/**} : caisses,
 * agences, centres payeurs, créneaux, salles, médecins, générateurs), mêmes règles (résiduel RF1-R4 clos).
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
    private static final java.util.regex.Pattern ADMINISTRATION_GENERIQUE =
            java.util.regex.Pattern.compile("/api/v1/admin/referentials(/|$)");

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
        if (!estUneRouteDeLAdministrationGenerique(requete)) {
            return gestionnaireCommun.handleUnexpected(erreur);
        }
        return reponse(erreur, requete, "Paramètre obligatoire absent : " + erreur.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail corpsIllisible(HttpMessageNotReadableException erreur, HttpServletRequest requete) {
        if (!estUneRouteDeLAdministrationGenerique(requete)) {
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

    /**
     * KB-RF1-01 (RF1c), KB-RF2-01 (RF2c) et KB-RF3-01 à 03 (RF3d, ADR 0129) : TOUTE l'administration générique des référentiels
     * ({@code /api/v1/admin/referentials} et ses sous-routes, quel que soit le référentiel : résiduel RF1-R4 clos).
     */
    private static boolean estUneRouteDeLAdministrationGenerique(HttpServletRequest requete) {
        // RF5 (revue complémentaire, constat 6) : ancré au début du chemin SANS le contexte applicatif (plus de find() sur l'URI complète)
        String uri = requete.getRequestURI();
        String contexte = requete.getContextPath();
        String chemin = contexte != null && !contexte.isEmpty() && uri.startsWith(contexte) ? uri.substring(contexte.length()) : uri;
        return ADMINISTRATION_GENERIQUE.matcher(chemin).lookingAt();
    }

    /** Les indicateurs médicaux de la Direction : le 403 passe avant le 400 (les autres routes gardent leur 400). */
    private static boolean estUneRouteAvecDroitsAVerifier(HttpServletRequest requete) {
        return estUneRouteDeLAdministrationGenerique(requete)
                || ROUTES_AVEC_DROITS_A_VERIFIER.stream().anyMatch(requete.getRequestURI()::endsWith);
    }

    private static boolean estUneRouteReprise(HttpServletRequest requete) {
        return ROUTES.stream().anyMatch(requete.getRequestURI()::endsWith) || estUneRouteDeLAdministrationGenerique(requete);
    }

    /** Évalue l'expression {@code @PreAuthorize} de la route avec la racine d'expressions de Spring Security (403 avant 400). */
    private static boolean droitsSuffisants(HttpServletRequest requete) {
        // RF5 (revue complémentaire, constat 5) : sur l'administration générique, pas de garde connue = refus (403), jamais 400 ; le test
        // d'architecture RoutesDeLAdministrationGeneriqueGardeesTest garantit qu'aucune route réelle n'est dans ce cas
        boolean parDefautRefuse = estUneRouteDeLAdministrationGenerique(requete);
        if (!(requete.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod methode)) return !parDefautRefuse;
        PreAuthorize garde = methode.getMethodAnnotation(PreAuthorize.class);
        if (garde == null) garde = org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(methode.getBeanType(), PreAuthorize.class);
        if (garde == null) return !parDefautRefuse;
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

        /** La Direction (KB-DIR-02) et le cahier de dialyse imprimé (PL5, KB-PL5-1 : un POST répondait 500). */
        private static final java.util.List<String> PREFIXES = java.util.List.of("/api/v1/direction/", "/api/v1/cahier-dialyse/",
                "/api/v1/facturation/synthese/");

        private final ApiExceptionHandler gestionnaireCommun;

        MethodesNonSupporteesDeLaDirectionAdvice(ApiExceptionHandler gestionnaireCommun) {
            this.gestionnaireCommun = gestionnaireCommun;
        }

        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        ResponseEntity<ProblemDetail> methodeNonSupportee(HttpRequestMethodNotSupportedException erreur, HttpServletRequest requete) {
            if (PREFIXES.stream().noneMatch(requete.getRequestURI()::startsWith)) {
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
