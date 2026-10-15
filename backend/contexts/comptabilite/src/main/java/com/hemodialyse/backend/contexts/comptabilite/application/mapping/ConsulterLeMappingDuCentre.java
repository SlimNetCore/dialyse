package com.hemodialyse.backend.contexts.comptabilite.application.mapping;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.OperationComptable;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.MappingComptableDuCentre;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Langage publié : le mapping effectif d'un centre, pour la génération des écritures (ventes, règlements, stock).
 */
public final class ConsulterLeMappingDuCentre implements MappingComptableDuCentre {

    private final MappingDuCentreVu mappings;

    public ConsulterLeMappingDuCentre(MappingDuCentreVu mappings) {
        this.mappings = mappings;
    }

    @Override
    public MappingPublie mapping(UUID centre) {
        MappingComptable mapping = mappings.effectif(new CentreId(centre));
        Map<String, String> journaux = new LinkedHashMap<>();
        for (OperationComptable operation : OperationComptable.values()) {
            journaux.put(operation.name(), mapping.journalDe(operation).valeur());
        }
        return new MappingPublie(centre, mapping.compteVentes(), mapping.compteClientPatient(), mapping.compteClientDefaut(),
                mapping.compteBanque(), mapping.compteCaisse(), mapping.compteTvaCollectee().orElse(null),
                mapping.stock().stock(), mapping.stock().consommation(), mapping.stock().facturesNonParvenues(),
                mapping.stock().boniInventaire(), mapping.stock().maliInventaire(), journaux);
    }
}
