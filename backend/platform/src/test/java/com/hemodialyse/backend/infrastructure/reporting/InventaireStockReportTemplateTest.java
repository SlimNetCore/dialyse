package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.domain.stock.model.Inventaire;
import com.hemodialyse.backend.domain.stock.model.LigneInventaire;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintFrame;
import net.sf.jasperreports.engine.JRPrintImage;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Procès-verbal d'inventaire : sur plusieurs pages, logo, société, centre et pagination sur chaque page ;
 * indicateurs et analyse des écarts calculés par le serveur ; police standard (aucune police externe requise).
 */
class InventaireStockReportTemplateTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 30, 10, 0, 0, 0, ZoneOffset.UTC);

    private static JasperReport compile() throws Exception {
        try (InputStream is = Files.newInputStream(Path.of("src/main/resources/reports/inventaire_stock.jrxml"))) {
            return JasperCompileManager.compileReport(is);
        }
    }

    private static byte[] png() throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        }
    }

    /**
     * Même forme que la requête du modèle : 3 lots par article, un écart de -2 (casse) un article sur quatre.
     */
    private static Collection<Map<String, ?>> rows(int articles) {
        List<Map<String, ?>> rows = new ArrayList<>();
        for (int a = 0; a < articles; a++) {
            for (int lot = 0; lot < 3; lot++) {
                boolean gap = lot == 0 && a % 4 == 0;
                Map<String, Object> r = new HashMap<>();
                r.put("ARTICLE_KEY", "A" + a);
                r.put("ARTICLE_CODE", "ART-" + a);
                r.put("ARTICLE_LIBELLE", "Dialyseur haute perméabilité n° " + a);
                r.put("UNITE", "U");
                r.put("LOT_LIBELLE", "L" + a + "-" + lot);
                r.put("DATE_PEREMPTION", Date.valueOf(LocalDate.of(2027, 1, 1).plusDays(a)));
                r.put("PEREMPTION_ETAT", a == 1 ? "EXPIRE" : null);
                r.put("QUANTITE_THEORIQUE", new BigDecimal("10"));
                r.put("QUANTITE_COMPTEE", gap ? new BigDecimal("8") : new BigDecimal("10"));
                r.put("NON_COMPTE", 0);
                r.put("ECART", gap ? new BigDecimal("-2") : BigDecimal.ZERO);
                r.put("PMP", new BigDecimal("1500.00"));
                r.put("VAL_ECART", gap ? new BigDecimal("-3000.00") : new BigDecimal("0.00"));
                r.put("COMPTE_PAR", "infirmier");
                r.put("MOTIF", gap ? "Casse" : null);
                rows.add(r);
            }
        }
        return rows;
    }

    private static Map<String, Object> params(int articles) throws Exception {
        List<LigneInventaire> lignes = new ArrayList<>();
        for (int a = 0; a < articles; a++) {
            UUID articleId = UUID.randomUUID();
            for (int lot = 0; lot < 3; lot++) {
                LigneInventaire l = LigneInventaire.theorique(articleId, "ART-" + a, "Dialyseur n° " + a, "U",
                        UUID.randomUUID(), "L" + a + "-" + lot, null, new BigDecimal("10"), new BigDecimal("1500"));
                boolean gap = lot == 0 && a % 4 == 0;
                l.compter(new BigDecimal(gap ? "8" : "10"), gap ? "CASSE" : null, "infirmier", NOW);
                lignes.add(l);
            }
        }
        Inventaire inv = Inventaire.ouvrir(UUID.randomUUID(), "INV-00042", LocalDate.of(2026, 9, 30), "Inventaire annuel",
                "pharma", NOW, lignes);
        inv.cloturer("pharma", NOW);

        Map<String, Object> p = new InventaireReportService(null).params(inv, "admin", NOW);
        p.put("CENTER_ID", inv.getCenterId().toString());
        p.put("SOCIETE_NOM", "Clinique Néphro Santé");
        p.put("SOCIETE_PIED_PAGE", "SARL au capital de 1 000 000 DA");
        p.put("SOCIETE_LOGO", new ByteArrayInputStream(png()));
        p.put("CENTRE_NOM", "Centre d'hémodialyse de Kouba");
        p.put("CENTRE_LIGNE", "Centre d'hémodialyse de Kouba · Alger");
        p.put(JRParameter.REPORT_FORMAT_FACTORY, new FrenchFormatFactory());
        return p;
    }

    private static void collect(List<JRPrintElement> elements, List<JRPrintElement> out) {
        for (JRPrintElement e : elements) {
            out.add(e);
            if (e instanceof JRPrintFrame f) collect(f.getElements(), out);
        }
    }

    @Test
    void everyPageCarriesLogoIdentityAndPageNumberAndTheReportExportsToPdf() throws Exception {
        JasperPrint print = JasperFillManager.fillReport(compile(), params(60), new JRMapCollectionDataSource(rows(60)));

        assertThat(print.getPages()).hasSizeGreaterThan(2);
        int total = print.getPages().size();
        List<String> allTexts = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            JRPrintPage page = print.getPages().get(i);
            List<JRPrintElement> all = new ArrayList<>();
            collect(page.getElements(), all);
            List<String> texts = all.stream().filter(JRPrintText.class::isInstance)
                    .map(e -> ((JRPrintText) e).getFullText()).toList();
            allTexts.addAll(texts);
            assertThat(all).as("logo page %d", i + 1).anyMatch(JRPrintImage.class::isInstance);
            assertThat(texts).as("page %d", i + 1)
                    .contains("Clinique Néphro Santé", "Centre d'hémodialyse de Kouba", "SARL au capital de 1 000 000 DA",
                            "Page " + (i + 1) + " / ", " " + total);
        }
        assertThat(allTexts).contains("CLÔTURÉ", "60 / 180", "15", "Casse", "-45\u00A0000,00");
        assertThat(allTexts).noneMatch(t -> t != null && t.contains("\u202F"));

        byte[] pdf = JasperExportManager.exportReportToPdf(print);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void theTemplateUsesOnlyStandardFonts() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/reports/inventaire_stock.jrxml"));
        assertThat(xml).doesNotContain("fontName=");
    }
}

