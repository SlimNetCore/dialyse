package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.application.gmao.InterventionSuiviQueryService.Evenement;
import com.hemodialyse.backend.domain.gmao.model.Equipement;
import com.hemodialyse.backend.domain.gmao.model.EvenementIntervention;
import com.hemodialyse.backend.domain.gmao.model.Intervenant;
import com.hemodialyse.backend.domain.gmao.model.Intervention;
import com.hemodialyse.backend.domain.gmao.model.LigneCoutIntervention;
import com.hemodialyse.backend.domain.gmao.model.PrioriteIntervention;
import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;
import com.hemodialyse.backend.domain.gmao.model.TypeEquipement;
import com.hemodialyse.backend.domain.gmao.model.TypeIntervenant;
import com.hemodialyse.backend.domain.gmao.model.TypeIntervention;
import com.hemodialyse.backend.domain.gmao.model.TypeLigneCout;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

    private static final OffsetDateTime DEBUT = OffsetDateTime.of(2026, 3, 10, 8, 30, 0, 0, ZoneOffset.UTC);
    private static final ZoneId ALGER = ZoneId.of("Africa/Algiers");

    private static JasperReport compile() throws Exception {
        try (InputStream is = Files.newInputStream(Path.of("src/main/resources/reports/bon_intervention.jrxml"))) {
            return JasperCompileManager.compileReport(is);
        }
    }

    private static Intervention intervention(UUID centreId, UUID equipementId, UUID intervenantId) {
        Intervention i = Intervention.creer(equipementId, centreId, TypeIntervention.CURATIVE, DEBUT, "Alarme de conductivité",
                intervenantId, StatutEquipement.HORS_SERVICE, "Alarme conductivité", PrioriteIntervention.URGENTE,
                DEBUT.plusHours(6), UUID.randomUUID());
        i.demarrer(UUID.randomUUID());
        i.terminer("Cellule remplacée", StatutEquipement.EN_SERVICE, DEBUT.plusHours(2).plusMinutes(15),
                "Cellule de conductivité usée", UUID.randomUUID());
        i.ajouterLigneCout(LigneCoutIntervention.creer(
                TypeLigneCout.PIECE, "Cellule de conductivité", new BigDecimal("2"), new BigDecimal("12500"), null), null);
        i.ajouterLigneCout(LigneCoutIntervention.creer(
                TypeLigneCout.INTERVENANT, "Temps intervenant", new BigDecimal("2.25"), new BigDecimal("2000"), null), null);
        return i;
    }

    private static Map<String, Object> params(Intervention i, UUID centreId) {
        Equipement e = Equipement.creer("G01", "Générateur G01", TypeEquipement.GENERATEUR_DIALYSE, "Fresenius", "5008S",
                "SN-123", DEBUT, centreId, null, UUID.randomUUID(), null, null);
        Intervenant tech = Intervenant.creer(centreId, "Karim Benali", TypeIntervenant.EXTERNE, null, null, null);
        List<Evenement> chrono = List.of(
                new Evenement(new EvenementIntervention(EvenementIntervention.Type.CREEE, DEBUT.minusHours(1), UUID.randomUUID()), "Samia Admin"),
                new Evenement(new EvenementIntervention(EvenementIntervention.Type.TERMINEE, DEBUT.plusHours(2), UUID.randomUUID()), "Karim Benali"));
        Map<String, Object> p = new BonInterventionReportService(null, null, null, null, null)
                .params(i, e, tech, chrono, ALGER, "admin", DEBUT.plusDays(1));
        p.put("CENTER_ID", centreId.toString());
        p.put("SOCIETE_NOM", "Clinique Néphro Santé");
        p.put("SOCIETE_PIED_PAGE", "SARL au capital de 1 000 000 DA");
        p.put("CENTRE_NOM", "Centre d'hémodialyse de Kouba");
        p.put("CENTRE_LIGNE", "Centre d'hémodialyse de Kouba · Alger");
        p.put(JRParameter.REPORT_FORMAT_FACTORY, new FrenchFormatFactory());
        return p;
    }

    private static Collection<Map<String, ?>> rows(Intervention i) {
        List<Map<String, ?>> rows = new ArrayList<>();
        for (LigneCoutIntervention l : i.getLignesCout()) {
            Map<String, Object> r = new HashMap<>();
            r.put("LIGNE_TYPE", l.getType() == TypeLigneCout.PIECE ? "Pièce" : "Honoraires intervenant");
            r.put("LIGNE_LIBELLE", l.getLibelle());
            r.put("LIGNE_QUANTITE", l.getQuantite());
            r.put("LIGNE_PRIX", l.getPrixUnitaire());
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
        Map<String, Object> p = params(intervention(centreId, UUID.randomUUID(), null), centreId);

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
        Intervention i = intervention(centreId, UUID.randomUUID(), null);

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
        Intervention i = Intervention.creer(UUID.randomUUID(), centreId, TypeIntervention.PREVENTIVE, DEBUT, "Contrôle annuel",
                null, StatutEquipement.EN_SERVICE, UUID.randomUUID());
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
