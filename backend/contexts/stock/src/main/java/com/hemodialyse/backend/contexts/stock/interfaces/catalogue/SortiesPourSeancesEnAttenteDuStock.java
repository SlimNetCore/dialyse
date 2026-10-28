package com.hemodialyse.backend.contexts.stock.interfaces.catalogue;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiesPourSeances;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.SortiesPourSeancesEnAttente;

import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié des sorties pour séances en attente de rapprochement, au-dessus du port des pièces.
 */
public class SortiesPourSeancesEnAttenteDuStock implements SortiesPourSeancesEnAttente {
    private final SortiesPourSeances pieces;

    public SortiesPourSeancesEnAttenteDuStock(SortiesPourSeances pieces) {
        this.pieces = pieces;
    }

    @Override
    public Optional<String> premiereNonRapprochee(UUID centre) {
        return pieces.premiereNonRapprochee(new CentreId(centre));
    }
}