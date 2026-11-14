package com.hemodialyse.backend.contexts.stock.contrat;

import com.hemodialyse.backend.contexts.stock.publishedlanguage.NumerotationDesPiecesFournie;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Double en mémoire du port fourni au Stock (S-DDL2, ADR 0185) : les compteurs appartiennent à la Comptabilité, qui dépend du Stock ;
 * les tests du Stock n'en connaissent donc aucune implémentation réelle. Même format que l'adapter de la Comptabilité (préfixe par
 * défaut puis compteur sur cinq chiffres, à partir de 1, par centre et par type). Le contrat de cet adapter vit dans la Comptabilité.
 */
public final class NumerotationDesPiecesEnMemoire implements NumerotationDesPiecesFournie {

    private final Map<String, Long> compteurs = new HashMap<>();

    @Override
    public synchronized String suivante(UUID centre, String cle, String prefixeParDefaut) {
        long suivant = compteurs.merge(centre + "/" + cle, 1L, Long::sum);
        return prefixeParDefaut + String.format("%05d", suivant);
    }
}
