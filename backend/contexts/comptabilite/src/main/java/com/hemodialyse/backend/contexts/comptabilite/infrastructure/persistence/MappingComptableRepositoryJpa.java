package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.ComptesDuStock;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.OperationComptable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter JPA du mapping ({@code mapping_comptable}, une ligne par centre). À la lecture, une colonne de stock ou de journal
 * vide (centre paramétré avant l'arrivée du stock) vaut sa valeur par défaut, et un compte de TVA collectée vide vaut 44571
 * (KB-CPT-14, reproduit : la réponse du PUT montre la TVA vide, toute relecture montre 44571).
 */
public class MappingComptableRepositoryJpa implements MappingComptableRepository {

    private static final String TVA_PAR_DEFAUT = "44571";

    private final MappingComptableSpringData donnees;

    public MappingComptableRepositoryJpa(MappingComptableSpringData donnees) {
        this.donnees = donnees;
    }

    private static MappingComptable versLeDomaine(MappingComptableJpa ligne) {
        ComptesDuStock defauts = ComptesDuStock.parDefaut();
        ComptesDuStock stock = new ComptesDuStock(
                ou(ligne.getCompteStock(), defauts.stock()),
                ou(ligne.getCompteConsommation(), defauts.consommation()),
                ou(ligne.getCompteFacturesNonParvenues(), defauts.facturesNonParvenues()),
                ou(ligne.getCompteBoniInventaire(), defauts.boniInventaire()),
                ou(ligne.getCompteMaliInventaire(), defauts.maliInventaire()));
        Map<OperationComptable, CodeJournal> choix = new EnumMap<>(OperationComptable.class);
        journal(choix, OperationComptable.VENTE, ligne.getJournalVente());
        journal(choix, OperationComptable.REGLEMENT_BANQUE, ligne.getJournalReglementBanque());
        journal(choix, OperationComptable.REGLEMENT_CAISSE, ligne.getJournalReglementCaisse());
        journal(choix, OperationComptable.STOCK_RECEPTION, ligne.getJournalStockReception());
        journal(choix, OperationComptable.STOCK_SORTIE, ligne.getJournalStockSortie());
        journal(choix, OperationComptable.STOCK_INVENTAIRE, ligne.getJournalStockInventaire());
        return MappingComptable.reconstitue(new CentreId(ligne.getCenterId()), ligne.getCompteVentes(),
                ligne.getCompteClientPatient(), ligne.getCompteClientDefaut(), ligne.getCompteBanque(), ligne.getCompteCaisse(),
                ou(ligne.getCompteTvaCollectee(), TVA_PAR_DEFAUT), stock, choix);
    }

    private static String ou(String valeur, String defaut) {
        return valeur == null || valeur.isBlank() ? defaut : valeur;
    }

    /**
     * Une colonne vide laisse l'opération sur son journal par défaut ; un code hors format est une erreur (400, KB-CPT-22).
     */
    private static void journal(Map<OperationComptable, CodeJournal> choix, OperationComptable operation, String code) {
        if (code != null && !code.isBlank()) {
            choix.put(operation, CodeJournal.de(code));
        }
    }

    @Override
    public Optional<MappingComptable> duCentre(CentreId centre) {
        return donnees.findByCenterId(centre.valeur()).map(MappingComptableRepositoryJpa::versLeDomaine);
    }

    @Override
    public void enregistrer(MappingComptable mapping) {
        MappingComptableJpa ligne = donnees.findByCenterId(mapping.centre().valeur())
                .orElseGet(() -> new MappingComptableJpa(UUID.randomUUID(), mapping.centre().valeur()));
        ligne.setCompteVentes(mapping.compteVentes());
        ligne.setCompteClientPatient(mapping.compteClientPatient());
        ligne.setCompteClientDefaut(mapping.compteClientDefaut());
        ligne.setCompteBanque(mapping.compteBanque());
        ligne.setCompteCaisse(mapping.compteCaisse());
        ligne.setCompteTvaCollectee(mapping.compteTvaCollectee().orElse(null));
        ligne.setCompteStock(mapping.stock().stock());
        ligne.setCompteConsommation(mapping.stock().consommation());
        ligne.setCompteFacturesNonParvenues(mapping.stock().facturesNonParvenues());
        ligne.setCompteBoniInventaire(mapping.stock().boniInventaire());
        ligne.setCompteMaliInventaire(mapping.stock().maliInventaire());
        ligne.setJournalVente(mapping.journalDe(OperationComptable.VENTE).valeur());
        ligne.setJournalReglementBanque(mapping.journalDe(OperationComptable.REGLEMENT_BANQUE).valeur());
        ligne.setJournalReglementCaisse(mapping.journalDe(OperationComptable.REGLEMENT_CAISSE).valeur());
        ligne.setJournalStockReception(mapping.journalDe(OperationComptable.STOCK_RECEPTION).valeur());
        ligne.setJournalStockSortie(mapping.journalDe(OperationComptable.STOCK_SORTIE).valeur());
        ligne.setJournalStockInventaire(mapping.journalDe(OperationComptable.STOCK_INVENTAIRE).valeur());
        donnees.saveAndFlush(ligne);
    }
}
