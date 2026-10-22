package com.hemodialyse.backend.contexts.absencespatients.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Commentaire d'une absence : rogné, vide = absent, mille caractères au plus (colonne de la table).
 */
class CommentaireTest {

    @Test
    void un_texte_nul_ou_blanc_n_est_pas_un_commentaire() {
        assertThat(Commentaire.de(null)).isEmpty();
        assertThat(Commentaire.de("")).isEmpty();
        assertThat(Commentaire.de(" \t ")).isEmpty();
        assertThatThrownBy(() -> new Commentaire(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Commentaire(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void le_texte_est_rogne() {
        assertThat(Commentaire.de("  Appel famille \n").orElseThrow().texte()).isEqualTo("Appel famille");
    }

    @Test
    void la_limite_est_mille_caracteres_apres_rognage() {
        assertThat(Commentaire.de(" " + "x".repeat(1000) + " ")).isPresent();
        assertThatThrownBy(() -> Commentaire.de("x".repeat(1001))).isInstanceOf(SaisieInvalide.class);
    }
}
