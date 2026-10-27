package com.hemodialyse.backend.contexts.planification.interfaces.http;

import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.SaisieInvalide;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Les refus métier du paramétrage de la planification, au format commun de la plateforme ({@code ProblemDetail} avec un
 * {@code code} stable) : 400 {@code BAD_REQUEST} pour une valeur inutilisable ou hors borne, 422 pour une règle violée (les codes
 * du legacy sont conservés), 409 {@code PLANIFICATION_ECRITURE_CONCURRENTE} pour une écriture concurrente.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {ParametresControleur.class, PreferencesControleur.class, InfirmiersControleur.class,
        RoulementControleur.class, AbsencesDesInfirmiersControleur.class, MonPlanningControleur.class, RemplacementsControleur.class,
        PresenceControleur.class, PropositionsControleur.class, SallesControleur.class})
class ErreursMetierDeLaPlanificationAdvice {

    @ExceptionHandler(com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDeLaPresence.ImpressionRefusee.class)
    ProblemDetail impressionRefusee(
            com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDeLaPresence.ImpressionRefusee erreur) {
        return probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle metier violee", erreur.getMessage(), erreur.code());
    }

    static ProblemDetail probleme(HttpStatus statut, String titre, String detail, String code) {
        ProblemDetail probleme = ProblemDetail.forStatus(statut);
        probleme.setTitle(titre);
        probleme.setDetail(detail);
        probleme.setProperty("code", code);
        return probleme;
    }

    @ExceptionHandler(SaisieInvalide.class)
    ProblemDetail saisieInvalide(SaisieInvalide erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(RegleDePlanificationViolee.class)
    ProblemDetail regleViolee(RegleDePlanificationViolee erreur) {
        return probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle metier violee", erreur.getMessage(), erreur.code());
    }

    @ExceptionHandler(EcritureConcurrente.class)
    ProblemDetail ecritureConcurrente(EcritureConcurrente erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "PLANIFICATION_ECRITURE_CONCURRENTE");
    }
}
