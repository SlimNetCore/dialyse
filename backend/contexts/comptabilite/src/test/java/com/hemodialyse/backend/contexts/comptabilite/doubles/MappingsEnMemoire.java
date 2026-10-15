package com.hemodialyse.backend.contexts.comptabilite.doubles;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.OperationComptable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Double en mémoire du mapping enregistré : une ligne par centre, même contrat que l'adaptateur JPA (voir les tests de
 * contrat), dont la relecture d'une TVA vide donne 44571 (KB-CPT-14).
 */
public class MappingsEnMemoire implements MappingComptableRepository {

    private final Map<CentreId, MappingComptable> mappings = new HashMap<>();

    private static MappingComptable relu(MappingComptable mapping) {
        if (mapping.compteTvaCollectee().isPresent()) {
            return mapping;
        }
        Map<OperationComptable, CodeJournal> journaux = new EnumMap<>(OperationComptable.class);
        for (OperationComptable operation : OperationComptable.values()) {
            journaux.put(operation, mapping.journalDe(operation));
        }
        return MappingComptable.reconstitue(mapping.centre(), mapping.compteVentes(), mapping.compteClientPatient(),
                mapping.compteClientDefaut(), mapping.compteBanque(), mapping.compteCaisse(), "44571", mapping.stock(), journaux);
    }

    @Override
    public Optional<MappingComptable> duCentre(CentreId centre) {
        return Optional.ofNullable(mappings.get(centre)).map(MappingsEnMemoire::relu);
    }

    @Override
    public void enregistrer(MappingComptable mapping) {
        mappings.put(mapping.centre(), mapping);
    }

    public int nombre() {
        return mappings.size();
    }
}
