package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.ArticleId;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SE3 : lecture et remplacement des raccourcis de consommables du centre, ports en mémoire. */
class RaccourcisDeConsommablesTest {

    private static final UUID ART_1 = new UUID(6, 1);
    private static final UUID ART_2 = new UUID(6, 2);
    private static final UUID ART_INACTIF = new UUID(6, 3);

    private Banc banc;

    @BeforeEach
    void preparer() {
        banc = new Banc();
        banc.articles.put(ART_1, new ConsommationsALaValidation.Article(ART_1, "ART-1", true));
        banc.articles.put(ART_2, new ConsommationsALaValidation.Article(ART_2, "ART-2", true));
        banc.articles.put(ART_INACTIF, new ConsommationsALaValidation.Article(ART_INACTIF, "ART-X", false));
    }

    @Test
    void should_replace_the_shortcuts_of_the_session_centre_and_read_them_back_in_order() {
        var ecrits = banc.remplacerLesRaccourcis().executer(null, List.of(ART_2, ART_1));

        assertThat(ecrits.articles()).containsExactly(new ArticleId(ART_2), new ArticleId(ART_1));
        assertThat(banc.consulterLesRaccourcis().executer(null).articles()).containsExactly(new ArticleId(ART_2), new ArticleId(ART_1));
        assertThat(banc.diffusionsRecues).isEmpty();
    }

    @Test
    void should_write_nothing_when_one_article_of_the_list_is_refused() {
        banc.remplacerLesRaccourcis().executer(null, List.of(ART_1));
        banc.raccourcis.remplacements = 0;

        assertThatThrownBy(() -> banc.remplacerLesRaccourcis().executer(null, List.of(ART_2, ART_INACTIF)))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Article inactif: ART-X");
        assertThatThrownBy(() -> banc.remplacerLesRaccourcis().executer(null, List.of(ART_2, new UUID(6, 99))))
                .isInstanceOf(SaisieInvalide.class);
        var treize = new ArrayList<UUID>();
        for (int i = 0; i < 13; i++) treize.add(new UUID(6, 100 + i));
        assertThatThrownBy(() -> banc.remplacerLesRaccourcis().executer(null, treize)).isInstanceOf(SaisieInvalide.class);

        assertThat(banc.raccourcis.remplacements).isZero();
        assertThat(banc.consulterLesRaccourcis().executer(null).articles()).containsExactly(new ArticleId(ART_1));
    }

    @Test
    void should_clear_every_shortcut_when_the_list_is_absent_KB_SEA_31_reproduced() {
        banc.remplacerLesRaccourcis().executer(null, List.of(ART_1));

        banc.remplacerLesRaccourcis().executer(null, null);

        assertThat(banc.consulterLesRaccourcis().executer(null).articles()).isEmpty();
    }

    @Test
    void should_refuse_a_centre_that_is_not_the_one_of_the_session_before_any_write() {
        assertThatThrownBy(() -> banc.remplacerLesRaccourcis().executer(CENTRE_B.valeur(), List.of(ART_1))).isInstanceOf(AccesRefuse.class);
        assertThatThrownBy(() -> banc.consulterLesRaccourcis().executer(CENTRE_B.valeur())).isInstanceOf(AccesRefuse.class);
        assertThat(banc.raccourcis.remplacements).isZero();
    }
}
