package com.hemodialyse.backend.contexts.documents.infrastructure.rendu;

import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aucun gabarit n'embarque de requête et le moteur n'ouvre aucune connexion (DOC5j, ADR 0202) : les lignes sont fournies par le contexte
 * demandeur, y compris pour un modèle PERSONNALISÉ déjà stocké qui porte encore l'ancienne requête (la source de données prime : son rendu
 * ne change pas) ; sans source de données, le rapport se remplit sans ligne.
 */
class GabaritsSansRequeteTest {

    private static final String GABARIT_AVEC_L_ANCIENNE_REQUETE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports" name="stocke" pageWidth="300" pageHeight="100"
                          columnWidth="280" leftMargin="10" rightMargin="10" topMargin="10" bottomMargin="10">
                <queryString><![CDATA[SELECT x FROM table_qui_n_existe_pas]]></queryString>
                <field name="X" class="java.lang.String"/>
                <detail><band height="20">
                    <textField><reportElement x="0" y="0" width="200" height="20"/><textFieldExpression><![CDATA[$F{X}]]></textFieldExpression></textField>
                </band></detail>
            </jasperReport>
            """;

    private static List<String> textes(JasperPrint print) {
        return print.getPages().stream().map(JRPrintPage::getElements).flatMap(List::stream)
                .filter((JRPrintElement e) -> e instanceof JRPrintText).map(e -> ((JRPrintText) e).getFullText()).toList();
    }

    @Test
    void un_modele_stocke_qui_porte_encore_l_ancienne_requete_est_rendu_par_les_lignes_fournies() throws Exception {
        JasperReport stocke = new CustomTemplateCompiler().compileNoCache(GABARIT_AVEC_L_ANCIENNE_REQUETE);
        Map<String, Object> parametres = new HashMap<>();
        parametres.put(JRParameter.REPORT_DATA_SOURCE, new JRMapCollectionDataSource(List.of(Map.of("X", "ligne un"), Map.of("X", "ligne deux"))));

        JasperPrint print = new JasperReportService().fillReport(stocke, parametres);

        assertThat(textes(print)).containsExactly("ligne un", "ligne deux");
    }

    @Test
    void un_rapport_sans_source_de_donnees_se_remplit_sans_ligne_et_sans_connexion() throws Exception {
        JasperReport stocke = new CustomTemplateCompiler().compileNoCache(GABARIT_AVEC_L_ANCIENNE_REQUETE);

        JasperPrint print = new JasperReportService().fillReport(stocke, new HashMap<>());

        assertThat(textes(print)).isEmpty();
    }

    /** Garde permanente : aucun gabarit livré ne déclare de requête ni ne nomme de table (les données viennent des contextes). */
    @Test
    void aucun_gabarit_livre_n_embarque_de_requete() throws IOException {
        Path reports = Path.of("src/main/resources/reports");
        try (Stream<Path> gabarits = Files.list(reports)) {
            List<Path> jrxml = gabarits.filter(p -> p.getFileName().toString().endsWith(".jrxml")).toList();
            assertThat(jrxml).as("gabarits livrés").hasSizeGreaterThanOrEqualTo(14);
            for (Path gabarit : jrxml) {
                String xml = Files.readString(gabarit);
                assertThat(xml).as(gabarit.getFileName().toString()).doesNotContain("queryString");
            }
        }
    }
}
