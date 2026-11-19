package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.consultation.CriteresDeFacturesInvalides;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Les refus de la consultation des factures (RG-FAC-046) au format commun ({@code ProblemDetail} avec un {@code code} stable, comme
 * {@link ClotureDeFacturationAdvice}) : 400 {@code PERIODE_INVALIDE}, {@code TRI_INVALIDE}, {@code ETAT_INVALIDE}, {@code MONTANT_INVALIDE},
 * et {@code PARAMETRE_INVALIDE} pour un paramètre illisible (nombre, date, UUID) ou obligatoire absent ({@code centerId}) — sans quoi le
 * gestionnaire commun répondrait 500. Ces deux derniers cas sont résolus AVANT le {@code @PreAuthorize} : un appelant sans le rôle
 * {@code ADMIN} reçoit 403 avant 400 (aucune information sur la forme de la route). Limité à {@link FacturesControleur}.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = FacturesControleur.class)
class FacturesAdvice {

    static final String PARAMETRE_INVALIDE = "PARAMETRE_INVALIDE";

    private static ProblemDetail requete(String detail, String code) {
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        probleme.setTitle("Requete invalide");
        probleme.setDetail(detail);
        probleme.setProperty("code", code);
        return probleme;
    }

    private static ProblemDetail refus() {
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        probleme.setTitle("Acces refuse");
        probleme.setDetail("Accès refusé");
        probleme.setProperty("code", "ACCESS_DENIED");
        return probleme;
    }

    /** Même règle que le {@code @PreAuthorize("hasRole('ADMIN')")} de {@link FacturesControleur}. */
    private static boolean administrateur() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        return authentification != null && authentification.isAuthenticated()
                && authentification.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    @ExceptionHandler(RequeteInvalide.class)
    ProblemDetail requeteInvalide(RequeteInvalide erreur) {
        return requete(erreur.getMessage(), erreur.code());
    }

    @ExceptionHandler(CriteresDeFacturesInvalides.class)
    ProblemDetail criteresInvalides(CriteresDeFacturesInvalides erreur) {
        return requete(erreur.getMessage(), erreur.code());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametreIllisible(MethodArgumentTypeMismatchException erreur) {
        if (!administrateur()) return refus();
        return requete("Paramètre illisible : " + erreur.getName(), PARAMETRE_INVALIDE);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail parametreAbsent(MissingServletRequestParameterException erreur) {
        if (!administrateur()) return refus();
        return requete("Paramètre obligatoire absent : " + erreur.getParameterName(), PARAMETRE_INVALIDE);
    }
}
