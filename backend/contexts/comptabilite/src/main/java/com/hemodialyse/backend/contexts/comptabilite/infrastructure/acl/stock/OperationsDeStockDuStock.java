package com.hemodialyse.backend.contexts.comptabilite.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.stock.OperationsDeStock;

import java.time.LocalDate;
import java.util.List;

/**
 * ACL vers le langage publié du Stock ({@code publishedlanguage.OperationsDeStock}, slice S4, comme les Absences pour
 * ADR 0041) : traduit les opérations valorisées du Stock vers le port de la Comptabilité. Aucune requête SQL sur les tables du
 * Stock ne subsiste côté Comptabilité.
 */
public final class OperationsDeStockDuStock implements OperationsDeStock {

    private final com.hemodialyse.backend.contexts.stock.publishedlanguage.OperationsDeStock stock;

    public OperationsDeStockDuStock(com.hemodialyse.backend.contexts.stock.publishedlanguage.OperationsDeStock stock) {
        this.stock = stock;
    }

    private static List<Montant> montants(List<com.hemodialyse.backend.contexts.stock.publishedlanguage.OperationsDeStock.Montant> lignes) {
        return lignes.stream().map(l -> new Montant(l.compteStockArticle(), l.compteChargeArticle(), l.montant())).toList();
    }

    @Override
    public List<CentreId> centres() {
        return stock.centres().stream().map(CentreId::new).toList();
    }

    @Override
    public List<Reception> receptions(CentreId centre, LocalDate du, LocalDate au) {
        return stock.receptions(centre.valeur(), du, au).stream()
                .map(r -> new Reception(r.bonId(), r.reference(), r.date(), montants(r.lignes()), r.articlesSansValeur())).toList();
    }

    @Override
    public List<SortiesDuJour> sorties(CentreId centre, LocalDate du, LocalDate au) {
        return stock.sorties(centre.valeur(), du, au).stream()
                .map(s -> new SortiesDuJour(s.jour(), montants(s.lignes()), s.articlesSansValeur())).toList();
    }

    @Override
    public List<Inventaire> inventaires(CentreId centre, LocalDate du, LocalDate au) {
        return stock.inventaires(centre.valeur(), du, au).stream()
                .map(i -> new Inventaire(i.inventaireId(), i.reference(), i.date(),
                        i.ecarts().stream().map(e -> new Ecart(e.compteStockArticle(), e.boni(), e.mali())).toList())).toList();
    }
}

