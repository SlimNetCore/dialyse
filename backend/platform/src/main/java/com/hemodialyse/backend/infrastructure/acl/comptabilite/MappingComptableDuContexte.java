package com.hemodialyse.backend.infrastructure.acl.comptabilite;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.MappingComptableDuCentre;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.MappingComptableDuCentre.MappingPublie;
import com.hemodialyse.backend.domain.comptabilite.port.MappingComptablePort;
import com.hemodialyse.backend.domain.comptabilite.valueobject.ComptesStock;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.MappingComptable;
import com.hemodialyse.backend.domain.comptabilite.valueobject.OperationComptable;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * ACL temporaire (retirée avec les slices C5 et C6) : le mapping comptable est tenu par le contexte {@code comptabilite} (C2) ;
 * la génération des écritures de ventes, de règlements et de stock encore en place le lit par le langage publié, traduit dans
 * son ancien modèle.
 */
@Component
public class MappingComptableDuContexte implements MappingComptablePort {

    private final MappingComptableDuCentre mappings;

    public MappingComptableDuContexte(MappingComptableDuCentre mappings) {
        this.mappings = mappings;
    }

    static MappingComptable traduire(MappingPublie publie) {
        Map<OperationComptable, JournalCode> journaux = new EnumMap<>(OperationComptable.class);
        publie.journaux().forEach((operation, code) -> journaux.put(OperationComptable.valueOf(operation), JournalCode.de(code)));
        return new MappingComptable(publie.centre(), publie.compteVentes(), publie.compteClientPatient(),
                publie.compteClientDefaut(), publie.compteBanque(), publie.compteCaisse(), publie.compteTvaCollectee(),
                new ComptesStock(publie.compteStock(), publie.compteConsommation(), publie.compteFacturesNonParvenues(),
                        publie.compteBoniInventaire(), publie.compteMaliInventaire()),
                journaux);
    }

    @Override
    public MappingComptable findByCenterId(UUID centerId) {
        return traduire(mappings.mapping(centerId));
    }
}
