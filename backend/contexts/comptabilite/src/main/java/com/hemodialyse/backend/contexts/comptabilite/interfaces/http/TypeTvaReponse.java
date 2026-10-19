package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.tva.TypeDeTvaVue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un type de TVA en JSON : mêmes champs que le legacy ({@code TypeTVA}).
 */
public record TypeTvaReponse(UUID id, UUID centerId, String libelle, BigDecimal taux, String typePrestation,
                             boolean exonere,
                             LocalDate dateDebutValidite, LocalDate dateFinValidite, String texteReference,
                             boolean actif,
                             OffsetDateTime createdAt, String createdBy) {

    static TypeTvaReponse depuis(TypeDeTvaVue vue) {
        return new TypeTvaReponse(vue.id(), vue.centre(), vue.libelle(), vue.taux(), vue.typePrestation(), vue.exonere(),
                vue.dateDebutValidite(), vue.dateFinValidite(), vue.texteReference(), vue.actif(), vue.creeLe(), vue.creePar());
    }
}

