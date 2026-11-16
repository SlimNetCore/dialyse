package com.hemodialyse.backend.contexts.documents.infrastructure.rendu;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POL1 (ADR 0208, option A de l'ADR 0162) : la police par défaut des documents PDF est DejaVu Sans, embarquée en Identity-H. Un gabarit
 * sans {@code fontName} rend le grec, le cyrillique, l'étoile et l'arabe (formes de présentation) sans les perdre, et conserve les accents,
 * Œ/œ et €. Le japonais et le chinois restent hors police (pas de CJK).
 */
class PolicesDesDocumentsPdfTest {

    private static final String GABARIT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports" name="polices" pageWidth="595" pageHeight="842"
                          columnWidth="535" leftMargin="30" rightMargin="30" topMargin="30" bottomMargin="30" whenNoDataType="AllSectionsNoDetail">
                <parameter name="TEXTE" class="java.lang.String"/>
                <title>
                    <band height="40">
                        <textField>
                            <reportElement x="0" y="0" width="535" height="30"/>
                            <textFieldExpression><![CDATA[$P{TEXTE}]]></textFieldExpression>
                        </textField>
                    </band>
                </title>
            </jasperReport>
            """;

    private static byte[] rendre(String texte) throws Exception {
        JasperReport rapport = JasperCompileManager.compileReport(new ByteArrayInputStream(GABARIT.getBytes(StandardCharsets.UTF_8)));
        Map<String, Object> parametres = new HashMap<>();
        parametres.put("TEXTE", texte);
        return new JasperReportService().generateFromCompiled(rapport, parametres, "PDF");
    }

    private static String texteExtrait(byte[] pdf) throws Exception {
        try (PDDocument document = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static Set<String> policesEmbarquees(byte[] pdf) throws Exception {
        Set<String> noms = new LinkedHashSet<>();
        try (PDDocument document = PDDocument.load(pdf)) {
            for (PDPage page : document.getPages()) {
                for (var nom : page.getResources().getFontNames()) {
                    PDFont police = page.getResources().getFont(nom);
                    assertThat(police.isEmbedded()).as("police " + police.getName() + " embarquee").isTrue();
                    noms.add(police.getName());
                }
            }
        }
        return noms;
    }

    @Test
    void un_texte_grec_cyrillique_etoile_et_arabe_est_rendu_dans_une_police_dejavu_embarquee() throws Exception {
        byte[] pdf = rendre("Αθήνα Москва ★ ✓ مرحبا");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(policesEmbarquees(pdf)).isNotEmpty().allMatch(nom -> nom.contains("DejaVuSans"));
        String texte = texteExtrait(pdf);
        assertThat(texte).contains("Αθήνα").contains("Москва").contains("★").contains("✓");
        // arabe : lettres isolees (pas de mise en forme bidi), l'ordre n'est pas garanti : chaque lettre doit etre presente
        for (char lettre : "مرحبا".toCharArray()) assertThat(texte).contains(String.valueOf(lettre));
    }

    @Test
    void les_accents_le_oe_ligature_et_l_euro_restent_rendus() throws Exception {
        byte[] pdf = rendre("Éléphant à Œuvre œuf ç ï ù 12,50 €");

        assertThat(policesEmbarquees(pdf)).allMatch(nom -> nom.contains("DejaVuSans"));
        assertThat(texteExtrait(pdf)).contains("Éléphant à Œuvre œuf ç ï ù 12,50 €");
    }
}
