package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.contexts.gmao.application.equipement.EquipementVue;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.IntervenantVue;
import com.hemodialyse.backend.contexts.gmao.application.intervention.DonneesDuBon;
import com.hemodialyse.backend.contexts.gmao.application.intervention.EvenementVu;
import com.hemodialyse.backend.contexts.gmao.application.intervention.InterventionVue;
import com.hemodialyse.backend.contexts.gmao.application.intervention.InterventionVue.LigneVue;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.TypeEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureIntervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.EvenementDeChronologie;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.PrioriteIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeLigneCout;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintFrame;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bon d'intervention : paramètres calculés par le serveur (dates UTC affichées dans le fuseau demandé), rendu du
 * modèle avec en-tête/pied d'identité, lignes de coût, totaux, et export PDF.
 */
class BonInterventionReportTemplateTest {

    private static final Instant DEBUT = Instant.parse("2026-03-10T08:30:00Z");
    private static final ZoneId ALGER = ZoneId.of("Africa/Algiers");

    private static JasperReport compile() throws Exception {
        try (InputStream is = Files.newInputStream(Path.of("src/main/resources/reports/bon_intervention.jrxml"))) {
            return JasperCompileManager.compileReport(is);
        }
    }

    private static LigneVue ligne(TypeLigneCout type, String libelle, String quantite, String prix) {
        BigDecimal q = new BigDecimal(quantite);
        BigDecimal p = new BigDecimal(prix);
        return new LigneVue(UUID.randomUUID(), type, libelle, q, p, q.multiply(p), null, false);
    }

    private static InterventionVue intervention(UUID centreId) {
        List<LigneVue> lignes = List.of(
                ligne(TypeLigneCout.PIECE, "Cellule de conductivité", "2", "12500"),
                ligne(TypeLigneCout.INTERVENANT, "Temps intervenant", "2.25", "2000"));
        return new InterventionVue(UUID.randomUUID(), UUID.randomUUID(), centreId, TypeIntervention.CURATIVE,
                StatutIntervention.TERMINEE, DEBUT, DEBUT.plusSeconds(2 * 3600 + 15 * 60), null, StatutEquipement.HORS_SERVICE,
                StatutEquipement.EN_SERVICE, PrioriteIntervention.URGENTE, DEBUT.plusSeconds(6 * 3600), "Alarme conductivité",
                "Cellule de conductivité usée", false, false, 0, "Alarme de conductivité", "Cellule remplacée", null, lignes,
                new BigDecimal("29500"), null, DEBUT, null, null, null, List.of());
    }

    private static InterventionVue sansLigne(UUID centreId) {
        return new InterventionVue(UUID.randomUUID(), UUID.randomUUID(), centreId, TypeIntervention.PREVENTIVE,
                StatutIntervention.PLANIFIEE, null, null, null, StatutEquipement.EN_SERVICE, null, PrioriteIntervention.NORMALE,
                null, null, null, false, false, 0, "Contrôle annuel", null, null, List.of(), BigDecimal.ZERO, null, DEBUT, null,
                null, null, List.of());
    }

    private static Map<String, Object> params(InterventionVue i, UUID centreId) {
        EquipementVue e = new EquipementVue(i.equipementId(), centreId, "G01", "Générateur G01", TypeEquipement.GENERATEUR_DIALYSE,
                "Fresenius", "5008S", "SN-123", DEBUT, StatutEquipement.EN_SERVICE, null, null, DEBUT, null, UUID.randomUUID(),
                null, null, null);
        IntervenantVue tech = new IntervenantVue(UUID.randomUUID(), centreId, "Karim Benali", NatureIntervenant.EXTERNE, null,
                null, null, true);
        List<EvenementVu> chrono = List.of(
                new EvenementVu(EvenementDeChronologie.Type.CREEE, DEBUT.minusSeconds(3600), UUID.randomUUID(), "Samia Admin", null),
                new EvenementVu(EvenementDeChronologie.Type.TERMINEE, DEBUT.plusSeconds(7200), UUID.randomUUID(), "Karim Benali", null));
        Map<String, Object> p = new BonInterventionReportService(null)
                .params(new DonneesDuBon(i, e, tech, chrono, DEBUT.plusSeconds(86400)), ALGER, "admin");
        p.put("CENTER_ID", centreId.toString());
        p.put("SOCIETE_NOM", "Clinique Néphro Santé");
        p.put("SOCIETE_PIED_PAGE", "SARL au capital de 1 000 000 DA");
        p.put("CENTRE_NOM", "Centre d'hémodialyse de Kouba");
        p.put("CENTRE_LIGNE", "Centre d'hémodialyse de Kouba · Alger");
        p.put(JRParameter.REPORT_FORMAT_FACTORY, new FrenchFormatFactory());
        return p;
    }

    private static Collection<Map<String, ?>> rows(InterventionVue i) {
        List<Map<String, ?>> rows = new ArrayList<>();
        for (LigneVue l : i.lignes()) {
            Map<String, Object> r = new HashMap<>();
            r.put("LIGNE_TYPE", l.type() == TypeLigneCout.PIECE ? "Pièce" : "Honoraires intervenant");
            r.put("LIGNE_LIBELLE", l.libelle());
            r.put("LIGNE_QUANTITE", l.quantite());
            r.put("LIGNE_PRIX", l.prixUnitaire());
            r.put("LIGNE_MONTANT", l.montant());
            rows.add(r);
        }
        return rows;
    }

    private static void collect(List<JRPrintElement> elements, List<JRPrintElement> out) {
        for (JRPrintElement e : elements) {
            out.add(e);
            if (e instanceof JRPrintFrame f) collect(f.getElements(), out);
        }
    }

    private static List<String> texts(JasperPrint print) {
        List<String> texts = new ArrayList<>();
        for (JRPrintPage page : print.getPages()) {
            List<JRPrintElement> all = new ArrayList<>();
            collect(page.getElements(), all);
            all.stream().filter(JRPrintText.class::isInstance).map(e -> ((JRPrintText) e).getFullText()).forEach(texts::add);
        }
        return texts;
    }

    @Test
    void params_should_format_utc_dates_in_the_requested_zone_and_compute_costs() {
        UUID centreId = UUID.randomUUID();
        Map<String, Object> p = params(intervention(centreId), centreId);

        assertThat(p.get("DATE_DEBUT")).isEqualTo("10/03/2026 09:30");   // 08:30 UTC = 09:30 à Alger (UTC+1)
        assertThat(p.get("DATE_FIN")).isEqualTo("10/03/2026 11:45");
        assertThat(p.get("DUREE")).isEqualTo("2 h 15");
        assertThat(p.get("BON_PRIORITE")).isEqualTo("Urgente");
        assertThat(p.get("ETAT_AVANT")).isEqualTo("Hors service");
        assertThat(p.get("COUT_PIECES")).isEqualTo("25 000,00 DA");
        assertThat(p.get("COUT_INTERVENANT")).isEqualTo("4 500,00 DA");
        assertThat(p.get("COUT_TOTAL")).isEqualTo("29 500,00 DA");
        assertThat((String) p.get("CONSTAT_TRAVAUX")).contains("Alarme conductivité", "Cellule de conductivité usée",
                "Cellule remplacée");
        assertThat((String) p.get("CHRONOLOGIE")).contains("Africa/Algiers", "Samia Admin");
        assertThat(p.get("SIGNATAIRE_CLOTURE")).isEqualTo("Karim Benali");
    }

    @Test
    void the_document_renders_identity_cost_lines_totals_and_exports_to_pdf() throws Exception {
        UUID centreId = UUID.randomUUID();
        InterventionVue i = intervention(centreId);

        JasperPrint print = JasperFillManager.fillReport(compile(), params(i, centreId), new JRMapCollectionDataSource(rows(i)));

        List<String> texts = texts(print);
        assertThat(texts).contains("Clinique Néphro Santé", "Centre d'hémodialyse de Kouba", "SARL au capital de 1 000 000 DA",
                "Cellule de conductivité", "Temps intervenant", "29 500,00 DA", "BON D'INTERVENTION");
        assertThat(texts).anyMatch(t -> t != null && t.startsWith("Page 1 / "));
        assertThat(texts).anyMatch(t -> t != null && t.contains("Karim Benali (externe)"));
        assertThat(new String(JasperExportManager.exportReportToPdf(print), 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void an_intervention_without_cost_lines_still_prints_its_sheet() throws Exception {
        UUID centreId = UUID.randomUUID();
        InterventionVue i = sansLigne(centreId);
        Map<String, Object> row = new HashMap<>();
        row.put("LIGNE_TYPE", null);
        row.put("LIGNE_LIBELLE", null);

        JasperPrint print = JasperFillManager.fillReport(compile(), params(i, centreId),
                new JRMapCollectionDataSource(List.of(row)));

        assertThat(texts(print)).contains("BON D'INTERVENTION", "0,00 DA");
        assertThat(new String(JasperExportManager.exportReportToPdf(print), 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void the_template_uses_only_standard_fonts() throws Exception {
        assertThat(Files.readString(Path.of("src/main/resources/reports/bon_intervention.jrxml"))).doesNotContain("fontName=");
    }
}
