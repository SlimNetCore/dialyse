package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormatDImpressionTest {

    @Test
    void should_default_to_pdf_when_absent_or_blank() {
        assertThat(FormatDImpression.depuis(null)).isEqualTo(FormatDImpression.PDF);
        assertThat(FormatDImpression.depuis("   ")).isEqualTo(FormatDImpression.PDF);
    }

    @Test
    void should_ignore_case_and_spaces() {
        assertThat(FormatDImpression.depuis("  html ")).isEqualTo(FormatDImpression.HTML);
        assertThat(FormatDImpression.depuis("xlsx")).isEqualTo(FormatDImpression.XLSX);
    }

    @Test
    void should_consider_excel_xls_and_xlsx_as_the_same_workbook() {
        assertThat(FormatDImpression.depuis("EXCEL").estUnClasseur()).isTrue();
        assertThat(FormatDImpression.depuis("XLS").estUnClasseur()).isTrue();
        assertThat(FormatDImpression.depuis("XLSX").estUnClasseur()).isTrue();
        assertThat(FormatDImpression.PDF.estUnClasseur()).isFalse();
        assertThat(FormatDImpression.HTML.estUnClasseur()).isFalse();
    }

    @Test
    void should_refuse_an_unknown_format_with_its_normalized_name() {
        assertThatThrownBy(() -> FormatDImpression.depuis(" docx "))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("format non supporte: DOCX");
    }
}
