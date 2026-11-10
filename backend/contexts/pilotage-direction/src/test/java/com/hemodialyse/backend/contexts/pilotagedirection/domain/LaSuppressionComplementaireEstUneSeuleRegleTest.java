package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KB-DIR-01 (revue L8, constat 3) : la suppression complémentaire d'un total a UNE définition ({@link SeuilDAnonymat#totalPublie}) et la
 * relecture d'un contenu déjà publié ({@link SeuilDAnonymat#totalPublieDepuis}) en est la même règle lue sur des valeurs masquées : sur un même
 * jeu, le calcul en direct et la relecture figée donnent le même total.
 */
class LaSuppressionComplementaireEstUneSeuleRegleTest {

    private static List<long[]> jeux() {
        return List.of(new long[]{7, 3, 4}, new long[]{55, 50, 5}, new long[]{12, 12, 0}, new long[]{4, 4, 0}, new long[]{9, 9},
                new long[]{0, 0, 0}, new long[]{14, 5, 9}, new long[]{10, 5, 5});
    }

    @Test
    void le_calcul_en_direct_et_la_relecture_figee_donnent_le_meme_total_sur_chaque_jeu() {
        for (long[] jeu : jeux()) {
            long total = jeu[0];
            List<Long> composants = Arrays.stream(jeu).skip(1).boxed().toList();

            Long enDirect = SeuilDAnonymat.totalPublie(total, composants);
            Long figee = SeuilDAnonymat.totalPublieDepuis(SeuilDAnonymat.masquer(total), composants.stream().map(c -> SeuilDAnonymat.masquer(c)).toList());

            assertThat(figee).as("jeu %s", Arrays.toString(jeu)).isEqualTo(enDirect);
        }
    }

    @Test
    void un_total_est_supprime_des_qu_un_composant_est_masque_et_garde_sinon() {
        assertThat(SeuilDAnonymat.totalPublie(7, List.of(3L, 4L))).isNull();
        assertThat(SeuilDAnonymat.totalPublie(55, List.of(50L, 5L))).isEqualTo(55L);
        assertThat(SeuilDAnonymat.totalPublie(12, List.of(12L, 0L))).as("zéro ne désigne personne").isEqualTo(12L);
        assertThat(SeuilDAnonymat.totalPublie(4, List.of(4L))).as("le total lui-même est masqué").isNull();
    }
}
