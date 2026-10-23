package com.hemodialyse.backend.contexts.stock.doubles;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.commandes.BonDeCommande;
import com.hemodialyse.backend.contexts.stock.domain.commandes.BonsDeCommande;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Double en mémoire du port des bons de commande : séquence {@code BC-nnnnn} par centre, atomique avec l'enregistrement.
 */
public class BonsEnMemoire implements BonsDeCommande {

    private final Map<UUID, BonDeCommande> bons = new LinkedHashMap<>();
    private final Map<CentreId, Integer> sequences = new LinkedHashMap<>();

    @Override
    public BonDeCommande creer(CentreId centre, Function<String, BonDeCommande> fabrique) {
        int suivant = sequences.getOrDefault(centre, 0) + 1;
        BonDeCommande bon = fabrique.apply(String.format("BC-%05d", suivant));
        sequences.put(centre, suivant);
        bons.put(bon.id(), bon);
        return bon;
    }

    @Override
    public Optional<BonDeCommande> parId(CentreId centre, UUID id) {
        return Optional.ofNullable(bons.get(id)).filter(b -> b.centre().equals(centre));
    }

    @Override
    public List<BonDeCommande> tous(CentreId centre) {
        List<BonDeCommande> resultat = new ArrayList<>(bons.values().stream().filter(b -> b.centre().equals(centre)).toList());
        resultat.sort(Comparator.comparing(BonDeCommande::reference).reversed());
        return resultat;
    }

    @Override
    public Optional<BonDeCommande> modifier(CentreId centre, UUID id, UnaryOperator<BonDeCommande> modification) {
        return parId(centre, id).map(b -> {
            BonDeCommande apres = modification.apply(b);
            bons.put(id, apres);
            return apres;
        });
    }

    public int sequence(CentreId centre) {
        return sequences.getOrDefault(centre, 0);
    }
}

