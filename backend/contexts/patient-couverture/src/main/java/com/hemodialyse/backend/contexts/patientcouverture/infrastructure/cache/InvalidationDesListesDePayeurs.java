package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.cache;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.function.Consumer;

/**
 * Invalide les listes en cache d'un niveau de payeurs (clé = identifiant du centre) APRÈS le commit de l'écriture : aucune lecture
 * concurrente ne remet en cache l'état d'avant. Hors transaction : tout de suite. Un niveau n'invalide que ses propres listes (et la
 * liste détaillée des centres payeurs), comme l'administration générique avant la refonte.
 */
public class InvalidationDesListesDePayeurs implements Consumer<CentreId> {

    public static final List<String> CAISSES = List.of("ref.caisses", "ref.centresPayeursDetails");
    public static final List<String> AGENCES = List.of("ref.agences", "ref.centresPayeursDetails");
    public static final List<String> CENTRES_PAYEURS = List.of("ref.centresPayeurs", "ref.centresPayeursDetails");

    private final CacheManager caches;
    private final List<String> listes;

    public InvalidationDesListesDePayeurs(CacheManager caches, List<String> listes) {
        this.caches = caches;
        this.listes = List.copyOf(listes);
    }

    @Override
    public void accept(CentreId centre) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalider(centre);
                }
            });
        } else {
            invalider(centre);
        }
    }

    private void invalider(CentreId centre) {
        for (String nom : listes) {
            Cache liste = caches.getCache(nom);
            if (liste != null) liste.evict(centre.valeur().toString());
        }
    }
}
