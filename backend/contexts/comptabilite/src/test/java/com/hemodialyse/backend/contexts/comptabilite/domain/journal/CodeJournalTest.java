package com.hemodialyse.backend.contexts.comptabilite.domain.journal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeJournalTest {

    @Test
    void le_code_est_rogne_et_normalise_en_majuscules() {
        assertThat(CodeJournal.de("  od ").valeur()).isEqualTo("OD");
        assertThat(CodeJournal.de("bq")).isEqualTo(CodeJournal.BQ);
        assertThat(CodeJournal.de("vE1").valeur()).isEqualTo("VE1");
    }

    @Test
    void les_bornes_a_z_et_0_9_sont_acceptees() {
        assertThat(CodeJournal.de("A").valeur()).isEqualTo("A");
        assertThat(CodeJournal.de("Z").valeur()).isEqualTo("Z");
        assertThat(CodeJournal.de("0").valeur()).isEqualTo("0");
        assertThat(CodeJournal.de("9").valeur()).isEqualTo("9");
        assertThat(CodeJournal.de("ABCDEFGHIJ").valeur()).hasSize(10);
    }

    @Test
    void un_code_absent_ou_blanc_est_refuse() {
        for (String code : new String[]{null, "", "  "}) {
            assertThatThrownBy(() -> CodeJournal.de(code)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Le code du journal est obligatoire");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABCDEFGHIJK", "@", "[", ":", "V-E", "V E", "V.E", "VÉ"})
    void un_code_hors_format_est_refuse(String code) {
        assertThatThrownBy(() -> CodeJournal.de(code)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Le code du journal comporte 1 à 10 lettres majuscules ou chiffres");
    }

    @Test
    void les_codes_par_defaut_sont_ve_bq_ca_ac_st() {
        assertThat(CodeJournal.VE).hasToString("VE");
        assertThat(CodeJournal.BQ).hasToString("BQ");
        assertThat(CodeJournal.CA).hasToString("CA");
        assertThat(CodeJournal.AC).hasToString("AC");
        assertThat(CodeJournal.ST).hasToString("ST");
    }

    @Test
    void les_codes_se_trient_par_ordre_alphabetique() {
        assertThat(CodeJournal.AC).isLessThan(CodeJournal.BQ);
    }
}
