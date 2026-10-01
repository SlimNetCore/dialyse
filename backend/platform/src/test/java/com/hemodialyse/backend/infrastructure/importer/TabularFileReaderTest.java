package com.hemodialyse.backend.infrastructure.importer;

import com.hemodialyse.backend.domain.referential.admin.model.ImportTable;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialField;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TabularFileReaderTest {

    private final TabularFileReader reader = new TabularFileReader();
    private final ReferentialTemplateWriter writer = new ReferentialTemplateWriter();

    @Test
    void readsSemicolonCsvWithBomQuotesAndLineNumbers() {
        String csv = "\uFEFFCode;Libellé\r\n"
                + "CR1;\"Matin; 06h30 – 10h30\"\r\n"
                + "\r\n"
                + "CR2;\"Après-midi\nlong\"\r\n"
                + "CR3;\"Il dit \"\"bonjour\"\"\"\r\n";

        ImportTable table = reader.read("creneaux.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(table.headers()).containsExactly("Code", "Libellé");
        assertThat(table.rows()).extracting(ImportTable.Row::lineNumber).containsExactly(2, 3, 4, 6);
        assertThat(table.rows().get(0).cells()).containsExactly("CR1", "Matin; 06h30 – 10h30");
        assertThat(table.rows().get(1).isBlank()).isTrue();
        assertThat(table.rows().get(2).cell(1)).isEqualTo("Après-midi\nlong");
        assertThat(table.rows().get(3).cell(1)).isEqualTo("Il dit \"bonjour\"");
    }

    @Test
    void detectsCommaDelimiterAndWindows1252Encoding() {
        byte[] content = "nom,telephone\nAmbulances Médéa,0795006136\n".getBytes(Charset.forName("windows-1252"));

        ImportTable table = reader.read("transporteurs.csv", content);

        assertThat(table.headers()).containsExactly("nom", "telephone");
        assertThat(table.rows().getFirst().cells()).containsExactly("Ambulances Médéa", "0795006136");
    }

    @Test
    void readsTheExcelTemplateItProduces() {
        ImportTable table = reader.read("modele.xlsx", writer.xlsx(ReferentialKind.SALLE));

        assertThat(table.headers()).containsExactlyElementsOf(
                ReferentialKind.SALLE.fields().stream().map(ReferentialField::label).toList());
        assertThat(table.rows()).hasSize(1);
        assertThat(table.rows().getFirst().lineNumber()).isEqualTo(2);
        assertThat(table.rows().getFirst().cells()).startsWith("S1", "Salle 1");
    }

    @Test
    void csvTemplateHeadersAreRecognisedByTheDomain() {
        ImportTable table = reader.read("modele.csv", writer.csv(ReferentialKind.CENTRE_PAYEUR));

        List<ReferentialField> fields = ReferentialKind.CENTRE_PAYEUR.fields();
        for (int i = 0; i < fields.size(); i++) {
            assertThat(fields.get(i).matchesHeader(ReferentialField.normalize(table.headers().get(i)))).isTrue();
        }
    }

    @Test
    void rejectsEmptyUnsupportedAndCorruptedFiles() {
        assertThatThrownBy(() -> reader.read("vide.csv", new byte[0]))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("IMPORT_EMPTY_FILE"));
        assertThatThrownBy(() -> reader.read("image.png", new byte[]{1, 2, 3}))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("IMPORT_UNSUPPORTED_FORMAT"));
        assertThatThrownBy(() -> reader.read("faux.xlsx", "pas un classeur".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("IMPORT_UNREADABLE_FILE"));
        assertThatThrownBy(() -> reader.read("abime.xlsx", new byte[]{'P', 'K', 3, 4, 0, 0, 0}))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("IMPORT_UNREADABLE_FILE"));
    }
}

