package com.hemodialyse.backend.contexts.documents.interfaces.http;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduRefuse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Les refus du rendu (type inconnu, modèle désactivé, génération impossible) au format commun de la plateforme : 422, titre « Regle metier
 * violee » et {@code code} stable, à l'identique de l'ancienne {@code BusinessException} du moteur (DOC2, ADR 0162). Sans restriction de
 * contrôleur : le rendu est demandé par les routes des contextes consommateurs (cahier, bon, PV, synthèse, rapport, planning).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class ErreursDuRenduAdvice {

    @ExceptionHandler(RenduRefuse.class)
    ProblemDetail renduRefuse(RenduRefuse refus) {
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        probleme.setTitle("Regle metier violee");
        probleme.setDetail(refus.getMessage());
        probleme.setProperty("code", refus.code());
        return probleme;
    }
}
