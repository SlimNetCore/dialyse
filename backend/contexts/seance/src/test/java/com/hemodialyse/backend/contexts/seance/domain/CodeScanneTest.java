package com.hemodialyse.backend.contexts.seance.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeScanneTest {

    @Test
    void should_refuse_a_missing_or_blank_qr() {
        assertThatThrownBy(() -> CodeScanne.lire(null)).isInstanceOf(SaisieInvalide.class).hasMessage("QR invalide");
        assertThatThrownBy(() -> CodeScanne.lire("   ")).isInstanceOf(SaisieInvalide.class).hasMessage("QR invalide");
    }

    @Test
    void should_refuse_a_qr_made_of_punctuation_only() {
        assertThatThrownBy(() -> CodeScanne.lire("-")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> CodeScanne.lire(" -:. ")).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_recognise_a_patient_identifier() {
        UUID patient = UUID.randomUUID();
        assertThat(CodeScanne.lire("  " + patient + " ").identifiant()).contains(patient);
        assertThat(CodeScanne.lire("PATIENT:" + patient).identifiant()).contains(patient);
        assertThat(CodeScanne.lire("P-001").identifiant()).isEmpty();
    }

    @Test
    void should_search_the_code_in_upper_case_after_the_prefix() {
        assertThat(CodeScanne.lire("pat-001").codePatient()).isEqualTo("PAT-001");
        assertThat(CodeScanne.lire("patient: pat-001 ").codePatient()).isEqualTo("PAT-001");
        assertThat(CodeScanne.lire("ass 12:34").numeroDAssurance()).isEqualTo("34");
    }

    @Test
    void should_not_cut_a_prefix_when_the_colon_is_first_or_last() {
        assertThat(CodeScanne.lire(":abc").codePatient()).isEqualTo(":ABC");
        assertThat(CodeScanne.lire("abc:").codePatient()).isEqualTo("ABC:");
    }

    @Test
    void should_match_tolerantly_ignoring_case_spaces_and_punctuation() {
        var qr = CodeScanne.lire("pat 001");
        assertThat(qr.correspond("PAT-001", null, null)).isTrue();
        assertThat(qr.correspond(null, "pat.001", null)).isTrue();
        assertThat(qr.correspond(null, null, "PAT/001")).isTrue();
        assertThat(qr.correspond("PAT-002", "X", "Y")).isFalse();
    }

    @Test
    void should_never_match_a_patient_without_references() {
        assertThat(CodeScanne.lire("abc").correspond(null, null, null)).isFalse();
        assertThat(CodeScanne.lire("abc").correspond("", "-", " ")).isFalse();
    }
}
