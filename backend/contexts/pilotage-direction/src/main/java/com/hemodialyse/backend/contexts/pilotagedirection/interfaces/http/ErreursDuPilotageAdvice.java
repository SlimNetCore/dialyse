package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.RegleDuPilotageViolee;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Les refus du Pilotage direction, au format commun de la plateforme ({@code ProblemDetail} + {@code code} stable) : 422 pour une période
 * invalide ({@code PERIODE_INVALIDE}) ou trop longue ({@code PERIODE_TROP_LONGUE}), titre « Regle metier violee » comme la
 * {@code BusinessException} du legacy dont elle reprend la réponse à l'identique (parité des goldens {@code bornes}).
 * <p>
 * Sans restriction de contrôleur : seul le Pilotage lève {@link RegleDuPilotageViolee}, et ses contrôleurs restent dans la plateforme
 * (jeton, garde de société, routes hébergées avec celles d'autres contextes : ADR 0102).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class ErreursDuPilotageAdvice {

    /** PL9 : date du rapport illisible = 400 (le 403 du profil sans droit passe avant : la garde précède le corps de la méthode). */
    @ExceptionHandler(RapportDeLaDirectionControleur.ParametreIllisible.class)
    ProblemDetail parametreIllisible(RapportDeLaDirectionControleur.ParametreIllisible erreur) {
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        probleme.setTitle("Requete invalide");
        probleme.setDetail("Paramètre illisible : " + erreur.nom());
        probleme.setProperty("code", "BAD_REQUEST");
        return probleme;
    }

    /**
     * PL9 : le rapport refuse un profil sans droit avant de lire les dates ; le legacy, qui lisait les dates d'abord, répondait « Accès refusé »
     * (et non « Access Denied ») quand une date était illisible : message d'origine restauré, 403 inchangé. Limité au contrôleur du rapport.
     */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @RestControllerAdvice(assignableTypes = {RapportDeLaDirectionControleur.class, TableauDeBordDeLaDirectionControleur.class,
            LecturesDeLaDirectionControleur.class})
    static class RefusDuRapportAdvice {

        @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
        ProblemDetail refus(org.springframework.security.access.AccessDeniedException erreur,
                            jakarta.servlet.http.HttpServletRequest requete) {
            boolean dateIllisible = requete.getRequestURI().endsWith("/report")
                    ? RapportDeLaDirectionControleur.dateIllisible(requete, "from") || RapportDeLaDirectionControleur.dateIllisible(requete, "to")
                    : !requete.getRequestURI().contains("/snapshots") && TableauDeBordDeLaDirectionControleur.parametreIllisible(requete);
            ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
            probleme.setTitle("Acces refuse");
            probleme.setDetail(dateIllisible ? "Accès refusé" : erreur.getMessage());
            probleme.setProperty("code", "ACCESS_DENIED");
            return probleme;
        }
    }

    @ExceptionHandler(RegleDuPilotageViolee.class)
    ProblemDetail regleViolee(RegleDuPilotageViolee erreur) {
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        probleme.setTitle("Regle metier violee");
        probleme.setDetail(erreur.getMessage());
        probleme.setProperty("code", erreur.code());
        return probleme;
    }
}
