package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.ArticleId;
import com.hemodialyse.backend.contexts.seance.domain.ArticlesDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.RaccourcisDeConsommables;
import com.hemodialyse.backend.contexts.seance.domain.RaccourcisDesCentres;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du port {@link RaccourcisDesCentres} : le même jeu de tests est rejoué sur le double en mémoire et sur la base (H2 et PostgreSQL
 * 16), ce qui garantit la fidélité du double.
 */
public abstract class RaccourcisContrat {

    protected static final CentreId CENTRE_A = new CentreId(new UUID(0, 0xA));
    protected static final CentreId CENTRE_B = new CentreId(new UUID(0, 0xB));
    /** Catalogue permissif : la composition des règles du catalogue est testée au domaine. */
    private static final ArticlesDuCentre TOUS = (centre, article) -> Optional.of(
            new com.hemodialyse.backend.contexts.seance.domain.ArticleDuCentre(article, "ART", true));

    protected abstract RaccourcisDesCentres depot();

    private static UUID a(int n) {
        return new UUID(4, n);
    }

    private static RaccourcisDeConsommables liste(CentreId centre, int... numeros) {
        List<UUID> ids = new ArrayList<>();
        for (int n : numeros) ids.add(a(n));
        return RaccourcisDeConsommables.composer(centre, ids, TOUS);
    }

    @Test
    void should_return_no_shortcut_for_a_centre_that_never_had_any() {
        assertThat(depot().du(CENTRE_A).articles()).isEmpty();
    }

    @Test
    void should_return_the_replaced_articles_in_the_order_of_display() {
        depot().remplacer(liste(CENTRE_A, 3, 1, 2));

        assertThat(depot().du(CENTRE_A).articles()).containsExactly(new ArticleId(a(3)), new ArticleId(a(1)), new ArticleId(a(2)));
    }

    @Test
    void should_replace_the_whole_list_and_clear_it_with_an_empty_list() {
        depot().remplacer(liste(CENTRE_A, 1, 2, 3));
        depot().remplacer(liste(CENTRE_A, 2));
        assertThat(depot().du(CENTRE_A).articles()).containsExactly(new ArticleId(a(2)));

        depot().remplacer(liste(CENTRE_A));
        assertThat(depot().du(CENTRE_A).articles()).isEmpty();
    }

    @Test
    void should_keep_each_centre_apart() {
        depot().remplacer(liste(CENTRE_A, 1, 2));
        depot().remplacer(liste(CENTRE_B, 2));

        depot().remplacer(liste(CENTRE_A, 3));

        assertThat(depot().du(CENTRE_A).articles()).containsExactly(new ArticleId(a(3)));
        assertThat(depot().du(CENTRE_B).articles()).containsExactly(new ArticleId(a(2)));
    }

    @Test
    void should_hold_twelve_articles() {
        depot().remplacer(liste(CENTRE_A, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12));

        assertThat(depot().du(CENTRE_A).articles()).hasSize(12).first().isEqualTo(new ArticleId(a(1)));
        assertThat(depot().du(CENTRE_A).articles()).last().isEqualTo(new ArticleId(a(12)));
    }
}
