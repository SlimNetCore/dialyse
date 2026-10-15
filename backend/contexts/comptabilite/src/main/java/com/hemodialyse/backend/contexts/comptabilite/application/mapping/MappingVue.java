package com.hemodialyse.backend.contexts.comptabilite.application.mapping;

import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.OperationComptable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Le mapping d'un centre tel que l'écran le lit : un compte de TVA collectée absent se lit {@code ""}, et chaque opération
 * porte le code de son journal.
 */
public record MappingVue(UUID centre, String compteVentes, String compteClientPatient, String compteClientDefaut,
                         String compteBanque, String compteCaisse, String compteTvaCollectee, String compteStock,
                         String compteConsommation, String compteFacturesNonParvenues, String compteBoniInventaire,
                         String compteMaliInventaire, Map<String, String> journaux) {

    static MappingVue depuis(MappingComptable mapping) {
        Map<String, String> journaux = new LinkedHashMap<>();
        for (OperationComptable operation : OperationComptable.values()) {
            journaux.put(operation.name(), mapping.journalDe(operation).valeur());
        }
        return new MappingVue(mapping.centre().valeur(), mapping.compteVentes(), mapping.compteClientPatient(),
                mapping.compteClientDefaut(), mapping.compteBanque(), mapping.compteCaisse(),
                mapping.compteTvaCollectee().orElse(""), mapping.stock().stock(), mapping.stock().consommation(),
                mapping.stock().facturesNonParvenues(), mapping.stock().boniInventaire(), mapping.stock().maliInventaire(),
                journaux);
    }
}
