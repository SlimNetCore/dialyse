package com.hemodialyse.backend.contexts.comptabilite.infrastructure.acl.facturation;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.vente.ReglementAComptabiliser;
import com.hemodialyse.backend.contexts.comptabilite.domain.vente.ReglementsAComptabiliser;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ReglementsEncaisses;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ReglementsEncaisses.ReglementEncaisse;

import java.time.LocalDate;
import java.util.List;

/**
 * Traduit les règlements encaissés fournis par la Facturation (langage publié) en règlements à comptabiliser. Un règlement
 * sans mode (d'avant la saisie du mode) est comptabilisé en banque comme avant (B13).
 */
public final class ReglementsAComptabiliserDepuisLaFacturation implements ReglementsAComptabiliser {

    private final ReglementsEncaisses encaisses;

    public ReglementsAComptabiliserDepuisLaFacturation(ReglementsEncaisses encaisses) {
        this.encaisses = encaisses;
    }

    static ReglementAComptabiliser traduire(ReglementEncaisse r) {
        return new ReglementAComptabiliser(r.id(), r.facture(), r.montant(), r.date(),
                r.mode() == null ? "BANQUE" : r.mode(), "Règlement facture", null);
    }

    @Override
    public List<ReglementAComptabiliser> encaissesEntre(CentreId centre, LocalDate du, LocalDate au) {
        return encaisses.encaissesEntre(centre.valeur(), du, au).stream()
                .map(ReglementsAComptabiliserDepuisLaFacturation::traduire).toList();
    }
}
