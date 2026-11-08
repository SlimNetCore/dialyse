package com.hemodialyse.backend.contexts.seance.infrastructure.cache;

import com.hemodialyse.backend.contexts.seance.domain.ArticleId;
import com.hemodialyse.backend.contexts.seance.domain.RaccourcisDeConsommables;
import com.hemodialyse.backend.contexts.seance.domain.RaccourcisDesCentres;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.cache.Cache;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

/**
 * Cache des raccourcis par centre (KB-SEA-32, reproduit) : la lecture est mise en cache sous la clé du centre (cache
 * {@code seance.raccourcis}, durée de vie des référentiels de la plateforme) ; un remplacement du centre purge SA ligne (jamais celle d'un
 * autre centre) ; un refus ne purge rien. Une écriture SQL directe (autre instance, script) reste invisible jusqu'au prochain
 * remplacement du centre ou à l'expiration (à vérifier si plusieurs instances partagent la base). Dans une transaction, la purge est posée
 * APRÈS sa validation ; hors transaction, elle est immédiate.
 */
public class RaccourcisDesCentresEnCache implements RaccourcisDesCentres {

    public static final String NOM_DU_CACHE = "seance.raccourcis";

    private final RaccourcisDesCentres source;
    private final Cache cache;

    public RaccourcisDesCentresEnCache(RaccourcisDesCentres source, Cache cache) {
        this.source = source;
        this.cache = cache;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RaccourcisDeConsommables du(CentreId centre) {
        List<UUID> ids = cache.get(centre.valeur(), () -> source.du(centre).articles().stream().map(ArticleId::valeur).toList());
        return RaccourcisDeConsommables.restituer(centre, ids.stream().map(ArticleId::new).toList());
    }

    @Override
    public void remplacer(RaccourcisDeConsommables raccourcis) {
        source.remplacer(raccourcis);
        UUID cle = raccourcis.centre().valeur();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // après la validation : purger avant lui laisserait une lecture concurrente remettre l'ancienne liste en cache
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.evict(cle);
                }
            });
        } else {
            cache.evict(cle);
        }
    }
}
