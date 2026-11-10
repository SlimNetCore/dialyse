package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Les refus des listes de choix des payeurs, au format commun de la plateforme (RF3c). Un identifiant de centre illisible
 * ({@code IllegalArgumentException} de la conversion) répond 400 {@code BAD_REQUEST} avec le message de la conversion, comme quand ces
 * routes étaient servies par l'Organisation (réponse inchangée). Un {@code centerId} absent de {@code /api/v1/referentials/*} répond aussi
 * 400 {@code BAD_REQUEST} (KB-RF3-03 corrigé, RF3d, ADR 0129 : même exception, levée par le contrôleur). Limité à ce contrôleur.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PayeursControleur.class)
class ErreursDesPayeursAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail identifiantIllisible(IllegalArgumentException erreur) {
        return RefusDeParametre.reponse(erreur);   // RF5 : liste blanche de messages attendus, sinon texte neutre
    }
}
