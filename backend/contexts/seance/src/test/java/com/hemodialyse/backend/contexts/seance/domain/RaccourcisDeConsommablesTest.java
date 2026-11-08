package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Raccourcis de consommables d'un centre : au plus douze, sans doublon, d'articles connus et actifs du centre. */
class RaccourcisDeConsommablesTest {

    private static final CentreId CENTRE = new CentreId(new UUID(0, 0xA));
    private final Map<UUID, ArticleDuCentre> catalogue = new HashMap<>();
    private final ArticlesDuCentre articles = (centre, article) -> Optional.ofNullable(catalogue.get(article.valeur()));

    private UUID article(int n, boolean actif) {
        UUID id = new UUID(3, n);
        catalogue.put(id, new ArticleDuCentre(new ArticleId(id), "ART-" + n, actif));
        return id;
    }

    @Test
    void should_keep_the_requested_order() {
        var a = article(1, true);
        var b = article(2, true);
        var c = article(3, true);

        var raccourcis = RaccourcisDeConsommables.composer(CENTRE, List.of(c, a, b), articles);

        assertThat(raccourcis.articles()).containsExactly(new ArticleId(c), new ArticleId(a), new ArticleId(b));
        assertThat(raccourcis.centre()).isEqualTo(CENTRE);
    }

    @Test
    void should_accept_twelve_and_refuse_thirteen_articles() {
        List<UUID> douze = new ArrayList<>();
        for (int i = 1; i <= 12; i++) douze.add(article(i, true));
        assertThat(RaccourcisDeConsommables.composer(CENTRE, douze, articles).articles()).hasSize(12);

        douze.add(article(13, true));
        assertThatThrownBy(() -> RaccourcisDeConsommables.composer(CENTRE, douze, articles))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Au plus 12 raccourcis de consommables");
    }

    @Test
    void should_refuse_a_duplicate_and_a_null_element_with_the_same_message() {
        var a = article(1, true);
        assertThatThrownBy(() -> RaccourcisDeConsommables.composer(CENTRE, List.of(a, a), articles))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Article en double ou invalide dans les raccourcis");
        var avecNul = new ArrayList<UUID>();
        avecNul.add(null);
        assertThatThrownBy(() -> RaccourcisDeConsommables.composer(CENTRE, avecNul, articles))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Article en double ou invalide dans les raccourcis");
    }

    @Test
    void should_refuse_an_unknown_article_and_an_inactive_article_naming_them() {
        var inactif = article(1, false);
        var inconnu = new UUID(3, 99);
        assertThatThrownBy(() -> RaccourcisDeConsommables.composer(CENTRE, List.of(inactif), articles))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Article inactif: ART-1");
        assertThatThrownBy(() -> RaccourcisDeConsommables.composer(CENTRE, List.of(inconnu), articles))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Article introuvable: " + inconnu);
    }

    @Test
    void should_check_the_articles_in_order_so_the_first_refusal_wins() {
        var inconnu = new UUID(3, 99);
        var a = article(1, true);
        assertThatThrownBy(() -> RaccourcisDeConsommables.composer(CENTRE, List.of(inconnu, a, a), articles))
                .hasMessage("Article introuvable: " + inconnu);
    }

    @Test
    void should_clear_every_shortcut_when_the_list_is_absent_or_empty_KB_SEA_31_reproduced() {
        assertThat(RaccourcisDeConsommables.composer(CENTRE, null, articles).articles()).isEmpty();
        assertThat(RaccourcisDeConsommables.composer(CENTRE, List.of(), articles).articles()).isEmpty();
    }

    @Test
    void should_restore_a_stored_list_without_replaying_the_catalogue_rules() {
        var restitues = RaccourcisDeConsommables.restituer(CENTRE, List.of(new ArticleId(new UUID(3, 7))));
        assertThat(restitues.articles()).containsExactly(new ArticleId(new UUID(3, 7)));
    }
}
