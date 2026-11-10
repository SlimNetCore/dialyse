package com.hemodialyse.backend.contexts.seance.interfaces.http;

import com.hemodialyse.backend.contexts.seance.application.AccesRefuse;
import com.hemodialyse.backend.contexts.seance.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRefusee;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Refus du volet paramédical (SE7f, ADR 0100), au format commun de la plateforme ({@code ProblemDetail} + {@code code} stable), codes du
 * legacy : 403 {@code ACCESS_DENIED}, 400 {@code BAD_REQUEST} (séance introuvable, patient introuvable, centre absent), 422 (facturée :
 * {@code SEANCE_FACTUREE_NON_MODIFIABLE} ; mesure refusée : {@code BUSINESS_ERROR}), 409 {@code ECRITURE_CONCURRENTE}. Corps illisible,
 * absent, mal typé, entier débordant ou identifiant de séance illisible : 400 {@code BAD_REQUEST} (KB-SEA-99, corrigé en L4 ; le legacy
 * répondait 500 {@code INTERNAL_ERROR}). Les erreurs d'accès aux données restent 500 {@code DATA_ACCESS_ERROR} (gestionnaire général).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = VoletParamedicalControleur.class)
class ErreursDuVoletParamedicalAdvice {

    private static ProblemDetail probleme(HttpStatus statut, String titre, String detail, String code) {
        ProblemDetail probleme = ProblemDetail.forStatus(statut);
        probleme.setTitle(titre);
        probleme.setDetail(detail);
        probleme.setProperty("code", code);
        return probleme;
    }

    @ExceptionHandler(SeanceRefusee.class)
    ProblemDetail seanceRefusee(SeanceRefusee erreur) {
        return probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle metier violee", erreur.getMessage(), erreur.motif().code());
    }

    @ExceptionHandler(SaisieInvalide.class)
    ProblemDetail saisieInvalide(SaisieInvalide erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(SeanceIntrouvable.class)
    ProblemDetail seanceIntrouvable(SeanceIntrouvable erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Seance introuvable", "BAD_REQUEST");
    }

    @ExceptionHandler(EcritureConcurrente.class)
    ProblemDetail ecritureConcurrente(EcritureConcurrente erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "ECRITURE_CONCURRENTE");
    }

    @ExceptionHandler(AccesRefuse.class)
    ProblemDetail accesRefuse(AccesRefuse erreur) {
        return probleme(HttpStatus.FORBIDDEN, "Acces refuse", erreur.getMessage(), "ACCESS_DENIED");
    }

    /**
     * KB-SEA-99 (corrigé, L4, ADR 0116) : corps illisible ou absent, mal typé, identifiant de séance illisible : 400 {@code BAD_REQUEST}
     * sans message technique, SAUF si le profil n'a pas le droit d'appeler la route (la lecture du corps précède {@code @PreAuthorize} :
     * sans cette garde, un profil refusé recevrait 400 et non 403). Même réponse que {@code ErreursDeSeanceAdvice}.
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail illisible(Exception erreur, HttpServletRequest requete) {
        if (!droitsSuffisants(requete)) return probleme(HttpStatus.FORBIDDEN, "Acces refuse", "Accès refusé", "ACCESS_DENIED");
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Corps ou paramètre illisible", "BAD_REQUEST");
    }

    private static boolean droitsSuffisants(HttpServletRequest requete) {
        if (!(requete.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod methode)) return true;
        PreAuthorize garde = methode.getMethodAnnotation(PreAuthorize.class);
        if (garde == null) return true;
        return DroitsDeLaRoute.suffisants(garde.value(), SecurityContextHolder.getContext().getAuthentication());
    }
}
