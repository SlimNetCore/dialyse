package com.hemodialyse.backend.contexts.seance.interfaces.http;

import com.hemodialyse.backend.contexts.seance.application.AccesRefuse;
import com.hemodialyse.backend.contexts.seance.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRefusee;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Refus du volet paramédical (SE7f, ADR 0100), au format commun de la plateforme ({@code ProblemDetail} + {@code code} stable), codes du
 * legacy : 403 {@code ACCESS_DENIED}, 400 {@code BAD_REQUEST} (séance introuvable, patient introuvable, centre absent), 422 (facturée :
 * {@code SEANCE_FACTUREE_NON_MODIFIABLE} ; mesure refusée : {@code BUSINESS_ERROR}), 409 {@code ECRITURE_CONCURRENTE}. Volontairement
 * SANS les handlers de corps illisible de {@code ErreursDeSeanceAdvice} : le legacy répondait 500 {@code INTERNAL_ERROR} à un JSON mal
 * formé, un corps absent, un identifiant illisible ou un entier débordant (goldens DM1 {@code erreurs}, {@code autres_mesures}), et ce
 * comportement est REPRODUIT (aucun écart décidé par SE7f) : ces erreurs tombent dans le gestionnaire général de la plateforme, comme les
 * erreurs d'accès aux données (500 {@code DATA_ACCESS_ERROR}).
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
}
