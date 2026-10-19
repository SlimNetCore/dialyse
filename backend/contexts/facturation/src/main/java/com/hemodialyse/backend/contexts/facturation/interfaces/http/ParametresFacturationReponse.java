package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturation;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Paramètres de facturation en JSON : mêmes champs que le legacy ; {@code updatedAt} est {@code null} tant que le centre n'a
 * rien enregistré (KB-FAC-08 corrigé).
 */
public record ParametresFacturationReponse(String codeFormat, boolean regroupementMultiForfait, OffsetDateTime updatedAt) {

    static ParametresFacturationReponse depuis(ParametresDeFacturation parametres) {
        return new ParametresFacturationReponse(parametres.modele().valeur(), parametres.regroupementMultiForfait(),
                parametres.misAJourLe() == null ? null : parametres.misAJourLe().atOffset(ZoneOffset.UTC));
    }
}
