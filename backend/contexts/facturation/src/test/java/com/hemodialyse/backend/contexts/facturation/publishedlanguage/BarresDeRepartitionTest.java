package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle.Repartition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BarresDeRepartitionTest {

    @Test
    void should_say_there_is_no_data_for_an_empty_distribution() {
        assertThat(BarresDeRepartition.texte(List.of())).isEqualTo("Aucune donnee pour la periode.");
    }

    @Test
    void should_fill_the_bar_of_the_maximum_and_scale_the_others_to_it() {
        String texte = BarresDeRepartition.texte(List.of(
                new Repartition("A", "Caisse A", 4, 8),
                new Repartition("B", "Caisse B", 2, 2)));

        assertThat(texte).isEqualTo("Caisse A\nP ########## 4 | S ########## 8\nCaisse B\nP #####----- 2 | S ###------- 2");
    }

    @Test
    void should_round_half_up_to_the_nearest_cell() {
        // 1/4 de 10 = 2,5 -> 3 ; 3/4 de 10 = 7,5 -> 8
        String texte = BarresDeRepartition.texte(List.of(
                new Repartition("A", "A", 4, 4),
                new Repartition("B", "B", 1, 3)));

        assertThat(texte).contains("P ###------- 1 | S ########-- 3");
    }

    @Test
    void should_draw_empty_bars_when_every_value_is_zero() {
        assertThat(BarresDeRepartition.texte(List.of(new Repartition("A", "A", 0, 0))))
                .isEqualTo("A\nP ---------- 0 | S ---------- 0");
    }

    @Test
    void should_keep_the_label_as_given_even_with_special_characters() {
        assertThat(BarresDeRepartition.texte(List.of(new Repartition("A", "L'<b>&]]>", 1, 1)))).startsWith("L'<b>&]]>\nP ");
    }
}
