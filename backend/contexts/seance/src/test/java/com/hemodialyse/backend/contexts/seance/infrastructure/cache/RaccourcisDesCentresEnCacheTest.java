package com.hemodialyse.backend.contexts.seance.infrastructure.cache;

import com.hemodialyse.backend.contexts.seance.contrat.RaccourcisDesCentresEnMemoire;
import com.hemodialyse.backend.contexts.seance.domain.ArticleId;
import com.hemodialyse.backend.contexts.seance.domain.RaccourcisDeConsommables;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** KB-SEA-32 reproduit : lecture mise en cache par centre, purge de SA ligne au remplacement, jamais celle d'un autre centre. */
class RaccourcisDesCentresEnCacheTest {

    private static final CentreId A = new CentreId(new UUID(0, 0xA));
    private static final CentreId B = new CentreId(new UUID(0, 0xB));

    private final RaccourcisDesCentresEnMemoire source = new RaccourcisDesCentresEnMemoire();
    private final RaccourcisDesCentresEnCache cache = new RaccourcisDesCentresEnCache(source, new ConcurrentMapCache("seance.raccourcis"));

    private static RaccourcisDeConsommables liste(CentreId centre, int... numeros) {
        var ids = new java.util.ArrayList<ArticleId>();
        for (int n : numeros) ids.add(new ArticleId(new UUID(4, n)));
        return RaccourcisDeConsommables.restituer(centre, ids);
    }

    @Test
    void should_keep_serving_the_cached_list_after_a_write_that_bypassed_the_cache() {
        assertThat(cache.du(A).articles()).isEmpty();

        source.remplacer(liste(A, 1)); // écriture directe : autre instance, script

        assertThat(cache.du(A).articles()).isEmpty();
    }

    @Test
    void should_purge_only_the_centre_that_was_replaced() {
        cache.du(A);
        cache.du(B);
        source.remplacer(liste(B, 9)); // invisible tant que B n'est pas purgé

        cache.remplacer(liste(A, 1));

        assertThat(cache.du(A).articles()).containsExactly(new ArticleId(new UUID(4, 1)));
        assertThat(cache.du(B).articles()).isEmpty();
    }

    @Test
    void should_read_the_stored_list_again_once_the_cache_is_cleared() {
        cache.du(A);
        source.remplacer(liste(A, 2, 1));

        assertThat(cache.du(A).articles()).isEmpty();
        cache.remplacer(liste(A, 2, 1));
        assertThat(cache.du(A).articles()).containsExactly(new ArticleId(new UUID(4, 2)), new ArticleId(new UUID(4, 1)));
        assertThat(List.copyOf(cache.du(A).articles())).hasSize(2);
    }
}
