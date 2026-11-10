package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FormatDImpression;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF4, constat 1 de la revue RF1-RF3d : le code d'une caisse saisi par un administrateur n'est JAMAIS du gabarit. Un code contenant
 * {@code ]]>}, {@code $P!{…}}, {@code $P{…}}, une apostrophe ou un retour à la ligne doit laisser l'impression réussir, et les montants
 * doivent rester rattachés à la colonne de cette caisse (le code est comparé tel quel, comme une valeur).
 */
class CodeDeCaissePiegeSyntheseMensuelleTest {

    private static final UUID CENTRE = UUID.fromString("99996800-0000-0000-0000-0000000000f5");
    private static final UUID AGENCE = UUID.fromString("99996800-0000-0000-0000-0000000000a1");

    @Test
    void un_code_de_caisse_piege_n_empeche_pas_l_impression_et_garde_ses_montants_dans_sa_colonne() throws Exception {
        String[] codes = {"a]]>b", "x$P!{CENTER_ID}y", "z$P{PERIOD_START}", "l'o'\"<&>", "ligne1\nligne2", "$", "{", "\\"};
        for (String code : codes) {
            assertThat(montantsImprimes(code)).as("code %s", code.replace("\n", "\\n")).containsExactly("100.00", "100.00");
        }
    }

    /** Imprime une facture de 100 HT rattachée à une agence dont la caisse porte {@code code} ; rend les textes « 100.00 » trouvés. */
    private static List<String> montantsImprimes(String code) throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:code_piege_" + Math.abs(code.hashCode()) + ";DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE IF NOT EXISTS factures (id UUID PRIMARY KEY, center_id UUID NOT NULL, agence_id_snapshot UUID, "
                + "date_facturation DATE NOT NULL, total_ht DECIMAL(14,2) NOT NULL, total_ttc DECIMAL(14,2) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS facture_lignes (id UUID PRIMARY KEY, facture_id UUID NOT NULL, center_id UUID NOT NULL, "
                + "forfait_label VARCHAR(255) NOT NULL, line_ht DECIMAL(14,2) NOT NULL)");
        UUID facture = UUID.randomUUID();
        jdbc.update("INSERT INTO factures VALUES (?, ?, ?, ?, ?, ?)", facture, CENTRE, AGENCE, Date.valueOf(LocalDate.of(2026, 2, 10)),
                new BigDecimal("100.00"), new BigDecimal("119.00"));
        jdbc.update("INSERT INTO facture_lignes VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), facture, CENTRE, "Forfait HD", new BigDecimal("100.00"));

        List<JasperPrint> rendus = new ArrayList<>();
        JasperReportService jasper = new JasperReportService(ds) {
            @Override
            public byte[] generateReport(String jrxmlPath, Map<String, Object> params, String format) throws java.io.IOException {
                try {
                    JasperReport rapport = compileReport(jrxmlPath);
                    rendus.add(fillReport(rapport, params));
                    return new byte[0];
                } catch (java.io.IOException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        SyntheseMensuelle synthese = new SyntheseMensuelle(CENTRE, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                List.of(new SyntheseMensuelle.Caisse(code, "Caisse piegee")), List.of(), List.of(), 1, 1, 1,
                new BigDecimal("77.00"), new BigDecimal("19.00"), new BigDecimal("96.00"));

        new FacturationSyntheseMensuelleReportService(jasper, centre -> Map.of(AGENCE, code)).imprimer(synthese, FormatDImpression.PDF);

        List<String> trouves = new ArrayList<>();
        for (JRPrintElement element : rendus.get(0).getPages().get(0).getElements()) {
            if (element instanceof JRPrintText texte && texte.getFullText() != null && texte.getFullText().matches("100[.,]00")) {
                trouves.add("100.00");
            }
        }
        return trouves;
    }
}
