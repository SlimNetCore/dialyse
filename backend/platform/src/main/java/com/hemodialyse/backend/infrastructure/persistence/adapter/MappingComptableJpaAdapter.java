package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.comptabilite.port.MappingComptablePort;
import com.hemodialyse.backend.domain.comptabilite.valueobject.ComptesStock;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.MappingComptable;
import com.hemodialyse.backend.domain.comptabilite.valueobject.OperationComptable;
import com.hemodialyse.backend.infrastructure.persistence.entity.MappingComptableJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.MappingComptableJpaRepository;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Component
public class MappingComptableJpaAdapter implements MappingComptablePort {

    private final MappingComptableJpaRepository repo;

    public MappingComptableJpaAdapter(MappingComptableJpaRepository repo) {
        this.repo = repo;
    }

    private static String ou(String valeur, String defaut) {
        return valeur == null || valeur.isBlank() ? defaut : valeur;
    }

    /**
     * Une colonne vide (centre paramétré avant l'arrivée du stock) laisse l'opération sur son journal par défaut.
     */
    private static void journal(Map<OperationComptable, JournalCode> journaux, OperationComptable operation, String code) {
        if (code != null && !code.isBlank()) {
            journaux.put(operation, JournalCode.de(code));
        }
    }

    @Override
    public MappingComptable findByCenterId(UUID centerId) {
        return repo.findByCenterId(centerId)
                .map(this::toDomain)
                .orElseGet(() -> MappingComptable.defaultFor(centerId));
    }

    @Override
    public void save(MappingComptable mapping) {
        MappingComptableJpaEntity entity = repo.findByCenterId(mapping.centerId())
                .orElseGet(() -> {
                    MappingComptableJpaEntity e = new MappingComptableJpaEntity();
                    e.setId(UUID.randomUUID());
                    e.setCenterId(mapping.centerId());
                    return e;
                });
        entity.setCompteVentes(mapping.compteVentes());
        entity.setCompteClientPatient(mapping.compteClientPatient());
        entity.setCompteClientCnas(mapping.compteClientCnas());
        entity.setCompteClientCasnos(mapping.compteClientCasnos());
        entity.setCompteClientMutuelle(mapping.compteClientMutuelle());
        entity.setCompteClientAutre(mapping.compteClientAutre());
        entity.setCompteBanque(mapping.compteBanque());
        entity.setCompteCaisse(mapping.compteCaisse());
        entity.setCompteTVACollectee(mapping.compteTVACollectee());
        ComptesStock stock = mapping.stock();
        entity.setCompteStock(stock.stock());
        entity.setCompteConsommation(stock.consommation());
        entity.setCompteFacturesNonParvenues(stock.facturesNonParvenues());
        entity.setCompteBoniInventaire(stock.boniInventaire());
        entity.setCompteMaliInventaire(stock.maliInventaire());
        entity.setJournalVente(mapping.journalDe(OperationComptable.VENTE).valeur());
        entity.setJournalReglementBanque(mapping.journalDe(OperationComptable.REGLEMENT_BANQUE).valeur());
        entity.setJournalReglementCaisse(mapping.journalDe(OperationComptable.REGLEMENT_CAISSE).valeur());
        entity.setJournalStockReception(mapping.journalDe(OperationComptable.STOCK_RECEPTION).valeur());
        entity.setJournalStockSortie(mapping.journalDe(OperationComptable.STOCK_SORTIE).valeur());
        entity.setJournalStockInventaire(mapping.journalDe(OperationComptable.STOCK_INVENTAIRE).valeur());
        repo.save(entity);
    }

    private MappingComptable toDomain(MappingComptableJpaEntity e) {
        ComptesStock defauts = ComptesStock.parDefaut();
        ComptesStock stock = new ComptesStock(
                ou(e.getCompteStock(), defauts.stock()),
                ou(e.getCompteConsommation(), defauts.consommation()),
                ou(e.getCompteFacturesNonParvenues(), defauts.facturesNonParvenues()),
                ou(e.getCompteBoniInventaire(), defauts.boniInventaire()),
                ou(e.getCompteMaliInventaire(), defauts.maliInventaire()));
        Map<OperationComptable, JournalCode> journaux = new EnumMap<>(OperationComptable.class);
        journal(journaux, OperationComptable.VENTE, e.getJournalVente());
        journal(journaux, OperationComptable.REGLEMENT_BANQUE, e.getJournalReglementBanque());
        journal(journaux, OperationComptable.REGLEMENT_CAISSE, e.getJournalReglementCaisse());
        journal(journaux, OperationComptable.STOCK_RECEPTION, e.getJournalStockReception());
        journal(journaux, OperationComptable.STOCK_SORTIE, e.getJournalStockSortie());
        journal(journaux, OperationComptable.STOCK_INVENTAIRE, e.getJournalStockInventaire());
        return new MappingComptable(e.getCenterId(),
                e.getCompteVentes(), e.getCompteClientPatient(),
                e.getCompteClientCnas(), e.getCompteClientCasnos(),
                e.getCompteClientMutuelle(), e.getCompteClientAutre(),
                e.getCompteBanque(), e.getCompteCaisse(),
                ou(e.getCompteTVACollectee(), "44571"), stock, journaux);
    }
}
