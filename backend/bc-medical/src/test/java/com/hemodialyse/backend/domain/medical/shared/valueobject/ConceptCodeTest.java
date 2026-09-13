package com.hemodialyse.backend.domain.medical.shared.valueobject;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConceptCodeTest {

    @Test
    void of_should_normalize_and_uppercase_code() {
        ConceptCode code = ConceptCode.of(CodingSystem.CIM10, " n18.5 ", "IRC stade 5");

        assertThat(code.code()).isEqualTo("N18.5");
        assertThat(code.display()).isEqualTo("IRC stade 5");
    }

    @Test
    void of_should_reject_blank_code() {
        assertThatThrownBy(() -> ConceptCode.of(CodingSystem.CIM10, " ", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void of_should_reject_invalid_cim10_format() {
        assertThatThrownBy(() -> ConceptCode.of(CodingSystem.CIM10, "ABCDEF", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void of_should_accept_cim10_without_subcategory() {
        ConceptCode code = ConceptCode.of(CodingSystem.CIM10, "I10", "Hypertension");

        assertThat(code.code()).isEqualTo("I10");
    }

    @Test
    void of_should_reject_invalid_loinc_format() {
        assertThatThrownBy(() -> ConceptCode.of(CodingSystem.LOINC, "not-a-loinc-code", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void of_should_accept_valid_loinc_code() {
        ConceptCode code = ConceptCode.of(CodingSystem.LOINC, "718-7", "Hémoglobine");

        assertThat(code.code()).isEqualTo("718-7");
    }

    @Test
    void equals_should_be_based_on_system_and_code_only() {
        ConceptCode a = ConceptCode.of(CodingSystem.CIM10, "N18.5", "Libellé A");
        ConceptCode b = ConceptCode.of(CodingSystem.CIM10, "N18.5", "Libellé B");

        assertThat(a).isEqualTo(b);
    }
}
