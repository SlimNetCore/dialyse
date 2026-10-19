package com.hemodialyse.backend.contexts.facturation.domain.parametres;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModeleDeNumerotationTest {

    @ParameterizedTest
    @ValueSource(strings = {"FAC-{YEAR}-{SEQ}", "F{YY}-{CENTER}-{SEQ6}", "{YYYY}{SEQ1}", "{SEQ99}", "FAC {SEQ} bis {SEQ4}"})
    void un_modele_avec_un_jeton_de_sequence_et_des_jetons_connus_est_accepte(String modele) {
        assertThat(ModeleDeNumerotation.saisi(modele).valeur()).isEqualTo(modele);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FACTURE", "FAC-{YEAR}", "FAC-{SEQ", "FAC-SEQ"})
    void un_modele_sans_jeton_de_sequence_est_refuse(String modele) {
        assertThatThrownBy(() -> ModeleDeNumerotation.saisi(modele)).isInstanceOf(ModeleDeNumerotationInvalide.class)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Le modèle de numérotation doit contenir le jeton de séquence {SEQ} ou {SEQn}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAC-{MOIS}-{SEQ}", "FAC-{seq}", "FAC-{SEQ0}", "FAC-{SEQ100}", "FAC-{}-{SEQ}", "FAC-{ YEAR }-{SEQ}"})
    void un_modele_avec_un_jeton_inconnu_est_refuse(String modele) {
        assertThatThrownBy(() -> ModeleDeNumerotation.saisi(modele)).isInstanceOf(ModeleDeNumerotationInvalide.class)
                .hasMessageStartingWith("Jeton inconnu {");
    }

    @Test
    void le_jeton_inconnu_est_cite() {
        assertThatThrownBy(() -> ModeleDeNumerotation.saisi("FAC-{MOIS}-{SEQ}"))
                .hasMessage("Jeton inconnu {MOIS} dans le modèle de numérotation : jetons admis {YYYY}, {YEAR}, {YY}, {CENTER}, "
                        + "{SEQ} ou {SEQn}");
    }

    @Test
    void un_modele_vide_ou_absent_est_refuse() {
        for (String vide : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> ModeleDeNumerotation.saisi(vide)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Le code de facturation est obligatoire");
        }
    }

    @Test
    void un_modele_tient_dans_la_colonne() {
        String juste = "{SEQ}" + "X".repeat(ModeleDeNumerotation.LONGUEUR_MAXIMALE - 5);
        assertThat(ModeleDeNumerotation.saisi(juste).valeur()).hasSize(120);
        assertThatThrownBy(() -> ModeleDeNumerotation.saisi(juste + "X")).isInstanceOf(ModeleDeNumerotationInvalide.class)
                .hasMessage("Le modèle de numérotation ne doit pas dépasser 120 caractères");
    }

    @Test
    void un_modele_relu_en_base_n_est_pas_revalide() {
        assertThat(new ModeleDeNumerotation("FACTURE").valeur()).isEqualTo("FACTURE");
        assertThat(ModeleDeNumerotation.PAR_DEFAUT.toString()).isEqualTo("FAC-{YEAR}-{SEQ}");
    }

    @Test
    void les_parametres_par_defaut_et_saisis() {
        assertThat(ParametresDeFacturation.PAR_DEFAUT.modele().valeur()).isEqualTo("FAC-{YEAR}-{SEQ}");
        assertThat(ParametresDeFacturation.PAR_DEFAUT.regroupementMultiForfait()).isTrue();
        assertThat(ParametresDeFacturation.PAR_DEFAUT.misAJourLe()).isNull();

        Instant maintenant = Instant.parse("2026-10-19T10:00:00Z");
        ParametresDeFacturation saisis = ParametresDeFacturation.saisis("F-{SEQ}", false, maintenant);
        assertThat(saisis.modele().valeur()).isEqualTo("F-{SEQ}");
        assertThat(saisis.regroupementMultiForfait()).isFalse();
        assertThat(saisis.misAJourLe()).isEqualTo(maintenant);
        assertThatThrownBy(() -> ParametresDeFacturation.saisis("FACTURE", true, maintenant))
                .isInstanceOf(ModeleDeNumerotationInvalide.class);
        assertThatThrownBy(() -> new ParametresDeFacturation(null, true, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
