package com.hemodialyse.backend.contexts.dossiermedical.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PL4 : le format d'export des statistiques est « csv » ou « pdf », sans egard a la casse ni aux espaces autour ; tout autre est refuse. */
class FormatDExportTest {

    @Test
    void should_lire_csv_et_pdf_sans_egard_a_la_casse_ni_aux_espaces() {
        assertThat(FormatDExport.lire("csv")).isEqualTo(FormatDExport.CSV);
        assertThat(FormatDExport.lire("CSV")).isEqualTo(FormatDExport.CSV);
        assertThat(FormatDExport.lire(" Pdf ")).isEqualTo(FormatDExport.PDF);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "xml", "csv,pdf", "xls"})
    void should_refuser_un_format_inconnu_ou_absent(String brut) {
        assertThatThrownBy(() -> FormatDExport.lire(brut))
                .isInstanceOf(FormatDExportInconnu.class)
                .isInstanceOf(SaisieInvalide.class);
    }
}
