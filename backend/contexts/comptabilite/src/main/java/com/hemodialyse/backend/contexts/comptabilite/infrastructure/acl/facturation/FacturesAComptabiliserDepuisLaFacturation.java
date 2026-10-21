package com.hemodialyse.backend.contexts.comptabilite.infrastructure.acl.facturation;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.vente.FactureAComptabiliser;
import com.hemodialyse.backend.contexts.comptabilite.domain.vente.FacturesAComptabiliser;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.FacturesEmises;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.FacturesEmises.FactureEmise;

import java.time.LocalDate;
import java.util.List;

/**
 * Traduit les factures émises fournies par la Facturation (langage publié) en factures à comptabiliser. Un total absent
 * vaut zéro.
 */
public final class FacturesAComptabiliserDepuisLaFacturation implements FacturesAComptabiliser {

    private final FacturesEmises emises;

    public FacturesAComptabiliserDepuisLaFacturation(FacturesEmises emises) {
        this.emises = emises;
    }

    static FactureAComptabiliser traduire(FactureEmise f) {
        return new FactureAComptabiliser(f.id(), f.numero(), f.payeur(),
                f.totalHt() != null ? f.totalHt() : java.math.BigDecimal.ZERO,
                f.totalTva() != null ? f.totalTva() : java.math.BigDecimal.ZERO,
                f.totalTtc() != null ? f.totalTtc() : java.math.BigDecimal.ZERO,
                f.dateFacturation(), f.finDePeriode(), f.libelle());
    }

    @Override
    public List<FactureAComptabiliser> dontLaPeriodeEstIncluseDans(CentreId centre, LocalDate debut, LocalDate fin) {
        return emises.dontLaPeriodeEstIncluseDans(centre.valeur(), debut, fin).stream()
                .map(FacturesAComptabiliserDepuisLaFacturation::traduire).toList();
    }

    @Override
    public List<FactureAComptabiliser> factureesEntre(CentreId centre, LocalDate du, LocalDate au) {
        return emises.emisesEntre(centre.valeur(), du, au).stream()
                .map(FacturesAComptabiliserDepuisLaFacturation::traduire).toList();
    }
}
