package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Les refus des règles du référentiel comptable : 422 avec le code d'erreur de la règle, au même format que le legacy
 * ({@code Regle metier violee}). Limité aux contrôleurs du contexte ; les autres erreurs (400 format, 403, 500) suivent
 * la gestion commune de la plateforme. Passe avant elle (la gestion commune attrape toute {@code Exception}).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {PlanComptableControleur.class, JournauxControleur.class, ComptesPayeursControleur.class,
        MappingControleur.class, EcrituresControleur.class, ModelesEtPiecesControleur.class})
class ReglesMetierVioleesAdvice {

    @ExceptionHandler(RegleMetierViolee.class)
    ProblemDetail reglesMetierViolees(RegleMetierViolee erreur) {
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        probleme.setTitle("Regle metier violee");
        probleme.setDetail(erreur.getMessage());
        probleme.setProperty("code", erreur.code());
        return probleme;
    }
}
