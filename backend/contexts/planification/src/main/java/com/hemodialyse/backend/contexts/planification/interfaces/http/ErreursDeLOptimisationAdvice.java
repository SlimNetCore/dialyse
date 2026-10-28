package com.hemodialyse.backend.contexts.planification.interfaces.http;

import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDuCalendrierDeLaProposition.ImpressionRefusee;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Set;

/**
 * Les refus de l'optimisation, au format commun ({@code ProblemDetail} avec un {@code code} stable) : 404 pour une exécution
 * introuvable (celle d'un autre centre comprise), 409 pour un calcul déjà en cours, une proposition déjà appliquée ou périmée et pour une
 * écriture concurrente (décision du 28/10, KB-PLN-91), 422 pour les autres règles violées, 400 pour une valeur inutilisable.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = OptimisationsControleur.class)
class ErreursDeLOptimisationAdvice {

    private static final Set<String> CONFLITS = Set.of("OPTIMISATION_DEJA_EN_COURS", "OPTIMISATION_DEJA_APPLIQUEE", "OPTIMISATION_PERIMEE");

    @ExceptionHandler(RegleDePlanificationViolee.class)
    ProblemDetail regleViolee(RegleDePlanificationViolee erreur) {
        if ("OPTIMISATION_INTROUVABLE".equals(erreur.code())) {
            return ErreursMetierDeLaPlanificationAdvice.probleme(HttpStatus.NOT_FOUND, "Introuvable", erreur.getMessage(), erreur.code());
        }
        if (CONFLITS.contains(erreur.code())) {
            return ErreursMetierDeLaPlanificationAdvice.probleme(HttpStatus.CONFLICT, "Conflit", erreur.getMessage(), erreur.code());
        }
        return ErreursMetierDeLaPlanificationAdvice.probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle metier violee", erreur.getMessage(),
                erreur.code());
    }

    @ExceptionHandler(ImpressionRefusee.class)
    ProblemDetail impressionRefusee(ImpressionRefusee erreur) {
        return ErreursMetierDeLaPlanificationAdvice.probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle metier violee", erreur.getMessage(),
                erreur.code());
    }

    @ExceptionHandler(SaisieInvalide.class)
    ProblemDetail saisieInvalide(SaisieInvalide erreur) {
        return ErreursMetierDeLaPlanificationAdvice.probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(EcritureConcurrente.class)
    ProblemDetail ecritureConcurrente(EcritureConcurrente erreur) {
        return ErreursMetierDeLaPlanificationAdvice.probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(),
                "PLANIFICATION_ECRITURE_CONCURRENTE");
    }
}
