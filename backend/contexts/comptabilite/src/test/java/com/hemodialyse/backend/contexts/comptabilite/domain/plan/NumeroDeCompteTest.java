package com.hemodialyse.backend.contexts.comptabilite.domain.plan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NumeroDeCompteTest {

    @Test
    void un_numero_de_un_a_vingt_lettres_ou_chiffres_est_accepte() {
        assertThat(NumeroDeCompte.de("7").valeur()).isEqualTo("7");
        assertThat(NumeroDeCompte.de("12345678901234567890").valeur()).hasSize(20);
    }

    @Test
    void les_espaces_de_bord_sont_rognes() {
        assertThat(NumeroDeCompte.de("  411999 ").valeur()).isEqualTo("411999");
    }

    @Test
    void les_minuscules_sont_normalisees_en_majuscules_comme_le_code_d_un_journal() {
        assertThat(NumeroDeCompte.de("abc9")).isEqualTo(NumeroDeCompte.de("ABC9"));
        assertThat(NumeroDeCompte.de("abc9").valeur()).isEqualTo("ABC9");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "123456789012345678901", "41-1", "41.1", "41_1", "41é", "41 1"})
    void un_numero_qui_n_a_pas_le_format_d_un_compte_est_refuse(String numero) {
        assertThatThrownBy(() -> NumeroDeCompte.de(numero)).isInstanceOf(NumeroDeCompteInvalide.class)
                .hasMessage("Un compte comporte 1 à 20 lettres ou chiffres");
    }

    @Test
    void les_numeros_se_trient_dans_l_ordre_alphabetique() {
        assertThat(NumeroDeCompte.de("10")).isLessThan(NumeroDeCompte.de("5121"));
        assertThat(NumeroDeCompte.de("5121")).isLessThan(NumeroDeCompte.de("9"));
    }

    @Test
    void un_numero_s_affiche_par_sa_valeur() {
        assertThat(NumeroDeCompte.de("512")).hasToString("512");
    }
}
