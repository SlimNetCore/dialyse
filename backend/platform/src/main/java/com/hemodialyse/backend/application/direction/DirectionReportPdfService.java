package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DirectionSnapshotService.Snapshot;
import com.hemodialyse.backend.application.direction.SocieteLetterheadPort.Letterhead;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Rapport de direction imprimable (PDF, A4 paysage) : en-tête et pied de page de la société (logo, coordonnées,
 * mentions légales, pied de page personnalisé, numéro de page) sur chaque page, puis l'ensemble des statistiques du
 * tableau de bord — synthèse, finances, évolution mensuelle, qualité des soins, sérologies, greffe, stock, alertes,
 * répartitions par sexe, âge et caisse d'assurance, traitement de l'anémie.
 * <p>
 * Deux sources : un instantané mensuel figé, ou les données en direct d'une période choisie. Agrégats anonymes
 * uniquement ; les valeurs masquées par l'anonymat restent masquées dans le document.
 */
@Service
public class DirectionReportPdfService {

    private static final Logger log = LoggerFactory.getLogger(DirectionReportPdfService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Map<String, String> AGE_LABELS = new LinkedHashMap<>();

    static {
        AGE_LABELS.put("0_17", "0-17 ans");
        AGE_LABELS.put("18_29", "18-29 ans");
        AGE_LABELS.put("30_44", "30-44 ans");
        AGE_LABELS.put("45_59", "45-59 ans");
        AGE_LABELS.put("60_PLUS", "60 ans et +");
        AGE_LABELS.put("INCONNU", "Non renseigné");
    }

    private final SocieteLetterheadPort letterheads;
    private final DirectionDashboardQueryService dashboard;
    private final DirectionIndicatorsQueryService indicators;
    private final DirectionBreakdownQueryService breakdowns;
    private final ObjectMapper mapper;

    public DirectionReportPdfService(SocieteLetterheadPort letterheads, DirectionDashboardQueryService dashboard,
                                     DirectionIndicatorsQueryService indicators,
                                     DirectionBreakdownQueryService breakdowns, ObjectMapper mapper) {
        this.letterheads = letterheads;
        this.dashboard = dashboard;
        this.indicators = indicators;
        this.breakdowns = breakdowns;
        this.mapper = mapper;
    }

    private static String css() {
        return "@page{size:A4 landscape;margin:34mm 12mm 16mm 12mm;"
                + "@top-center{content:element(letterhead)}@bottom-center{content:element(pagefoot)}"
                + "@bottom-right{content:'Page ' counter(page) ' / ' counter(pages);font-size:8px;color:#555}}"
                + "#letterhead{position:running(letterhead);width:100%}#pagefoot{position:running(pagefoot);width:100%}"
                + "body{font-family:Arial,sans-serif;font-size:9.5px;color:#111}"
                + "h1{font-size:19px;margin:0 0 2px;color:#0d5750}h2{font-size:13px;margin:16px 0 6px;color:#0d5750;"
                + "border-bottom:1px solid #b9d3cf;padding-bottom:2px}"
                + ".muted{color:#555;margin-bottom:8px}table{width:100%;border-collapse:collapse;margin-bottom:6px}"
                + "th,td{border:1px solid #ccd6d9;padding:3px 5px;text-align:right}"
                + "th:first-child,td:first-child{text-align:left}th{background:#eaf3f2;color:#0d5750}"
                + "tr.total td{font-weight:bold;background:#f4f8f8}"
                + ".kpis{width:100%;border-collapse:separate;border-spacing:6px 0;margin:8px -6px}"
                + ".kpis td{border:1px solid #cfe0de;border-radius:6px;background:#f4faf9;text-align:left;padding:6px 8px;width:12.5%}"
                + ".kpis .l{font-size:8px;color:#5b6b75;text-transform:uppercase}.kpis .v{font-size:14px;font-weight:bold;color:#0d5750}"
                + ".bar{height:6px;background:#e3ecee;border-radius:3px;margin-top:2px}.bar div{height:6px;background:#14766e;border-radius:3px}"
                + ".crit{color:#c62828;font-weight:bold}.warn{color:#c2620a;font-weight:bold}.ok{color:#2e7d32}"
                + ".alerts{margin:6px 0}.alert{padding:3px 8px;margin-bottom:3px;border-left:4px solid #ed6c02;background:#fdf3e8}"
                + ".alert.critical{border-left-color:#c62828;background:#fdecec}"
                + ".note{font-size:8px;color:#555;margin-top:10px}.two{width:100%;border:0}.two td{border:0;vertical-align:top;width:50%}"
                + ".two td:first-child{padding-right:8px}"
                + "h2{page-break-after:avoid}tr{page-break-inside:avoid}.two{page-break-inside:avoid}";
    }

    private static String logoImg(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return "";
        String type = bytes[0] == (byte) 0xFF ? "image/jpeg" : "image/png";
        return "<img style='max-width:64px;max-height:50px' src='data:" + type + ";base64,"
                + java.util.Base64.getEncoder().encodeToString(bytes) + "'/>";
    }

    private static void kpi(StringBuilder sb, String label, String value) {
        sb.append("<td><div class='l'>").append(esc(label)).append("</div><div class='v'>").append(value).append("</div></td>");
    }

    // ───────────────────────────── Document ─────────────────────────────

    private static String alertLabel(String code) {
        return switch (code) {
            case "KTV_CONFORMITE_BASSE" -> "Kt/V dans la cible pour trop peu de patients";
            case "HB_HORS_CIBLE" -> "Hémoglobine dans la cible pour trop peu de patients";
            case "OBSERVANCE_EN_RETARD" -> "Traitement de l'anémie en retard d'administration";
            case "STOCK_SOUS_SEUIL" -> "Articles sous le seuil d'alerte";
            case "LOTS_PERIMES" -> "Lots périmés encore en stock";
            case "LOTS_PEREMPTION_PROCHE" -> "Lots à péremption sous 90 jours";
            default -> code;
        };
    }

    private static boolean absent(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull();
    }

    private static String text(JsonNode n) {
        return absent(n) ? "—" : n.asString();
    }

    /**
     * Effectif de patients : masqué (« &lt; seuil ») quand l'anonymat l'impose.
     */
    private static String count(JsonNode n) {
        return absent(n) ? "&lt; seuil" : esc(n.asString());
    }

    private static String num(JsonNode n) {
        return absent(n) ? "—" : esc(n.asString());
    }

    // ───────────────────────────── Sections ─────────────────────────────

    private static String pct(JsonNode n) {
        return absent(n) ? "—" : esc(n.asString()) + " %";
    }

    private static String money(JsonNode n) {
        return absent(n) ? "—" : money(n.asDouble());
    }

    private static String money(double value) {
        return String.format(Locale.FRANCE, "%,.0f DA", value).replace(' ', ' ').replace(' ', ' ');
    }

    private static String money(BigDecimal value) {
        return money(value.doubleValue());
    }

    private static String bar(double value, double max) {
        int width = max <= 0 ? 0 : (int) Math.round(Math.min(100, value * 100 / max));
        return "<div class='bar'><div style='width:" + width + "%'></div></div>";
    }

    private static String cls(JsonNode n, String cssClass) {
        return !absent(n) && n.asDouble() > 0 ? " class='" + cssClass + "'" : "";
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /**
     * Rapport d'un mois figé.
     */
    public byte[] pdf(UUID societeId, Snapshot snapshot) {
        return render(html(societeId, "Rapport mensuel — " + snapshot.mois(), snapshot.generatedAt(),
                snapshot.overview(), snapshot.indicators(), snapshot.breakdown()));
    }

    /**
     * Rapport en direct sur la période choisie (par défaut : depuis le 1er janvier).
     */
    public byte[] pdf(UUID societeId, LocalDate from, LocalDate to) {
        var overview = dashboard.overview(societeId, from, to);
        JsonNode overviewNode = mapper.valueToTree(overview);
        return render(html(societeId, "Rapport de direction", OffsetDateTime.now(ZoneOffset.UTC), overviewNode,
                mapper.valueToTree(indicators.indicators(societeId, overview.from(), overview.to())),
                mapper.valueToTree(breakdowns.breakdown(societeId, overview.from(), overview.to()))));
    }

    private byte[] render(String html) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Rapport de direction : échec du rendu PDF", e);
            String cause = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new IllegalStateException("Impossible de générer le rapport PDF (" + cause + ")", e);
        }
    }

    String html(UUID societeId, String title, OffsetDateTime generatedAt, JsonNode overview, JsonNode ind,
                JsonNode breakdown) {
        Optional<Letterhead> letterhead = letterheads.forSociete(societeId);
        String societeNom = letterhead.map(Letterhead::nom).filter(n -> !n.isBlank()).orElse(text(overview.path("societeNom")));
        String period = text(overview.path("from")) + " au " + text(overview.path("to"));

        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset='UTF-8'/><style>").append(css()).append("</style></head><body>");
        sb.append(letterheadHtml(letterhead, societeNom, title, period));
        sb.append(footerHtml(letterhead, societeNom, generatedAt));

        sb.append("<h1>").append(esc(title)).append("</h1>");
        sb.append("<div class='muted'>").append(esc(societeNom)).append(" — période ").append(esc(period))
                .append(" — données au ").append(generatedAt == null ? "—" : generatedAt.format(STAMP))
                .append(" — agrégats anonymes (seuil d'anonymat : ").append(esc(text(overview.path("seuilAnonymat"))))
                .append(")</div>");

        summary(sb, overview.path("totaux"));
        alerts(sb, ind.path("alertes"));
        finances(sb, overview);
        monthly(sb, overview.path("mensuel"));
        clinical(sb, ind);
        stock(sb, ind);
        breakdown(sb, breakdown);
        sb.append("<p class='note'>Les effectifs de moins de ")
                .append(esc(text(overview.path("seuilAnonymat"))))
                .append(" patients sont masqués (« &lt; seuil ») et les taux calculés sur moins de patients évalués ne ")
                .append("sont pas publiés. Cibles cliniques : KDIGO (Kt/V &gt;= 1,2 ; hémoglobine 10 à 11,5 g/dL ; ")
                .append("phosphore 2,5 à 4,5 mg/dL ; PTH 130 à 600 pg/mL ; albumine &gt;= 4 g/dL).</p>");
        return sb.append("</body></html>").toString();
    }

    private String letterheadHtml(Optional<Letterhead> lh, String societeNom, String title, String period) {
        StringBuilder sb = new StringBuilder("<div id='letterhead'><table style='width:100%;border:0;margin:0'><tr>");
        String logo = lh.map(Letterhead::logo).map(DirectionReportPdfService::logoImg).orElse("");
        sb.append("<td style='width:72px;border:0;text-align:left;vertical-align:top'>").append(logo).append("</td>");
        sb.append("<td style='border:0;text-align:left;vertical-align:top'><div style='font-size:14px;font-weight:bold'>")
                .append(esc(societeNom)).append("</div>");
        lh.ifPresent(l -> {
            if (!l.contact().isBlank())
                sb.append("<div style='font-size:8px;color:#333'>").append(esc(l.contact())).append("</div>");
            if (!l.legal().isBlank())
                sb.append("<div style='font-size:8px;color:#333'>").append(esc(l.legal())).append("</div>");
        });
        sb.append("</td><td style='border:0;text-align:right;vertical-align:top;font-size:9px;color:#333'>")
                .append("<div style='font-size:11px;font-weight:bold'>").append(esc(title)).append("</div>")
                .append("<div>").append(esc(period)).append("</div></td></tr></table>")
                .append("<div style='border-top:2px solid #0d5750;margin-top:3px'></div></div>");
        return sb.toString();
    }

    private String footerHtml(Optional<Letterhead> lh, String societeNom, OffsetDateTime generatedAt) {
        String pied = lh.map(Letterhead::piedPage).filter(p -> !p.isBlank()).orElse("");
        return "<div id='pagefoot'><div style='border-top:1px solid #999;padding-top:3px;text-align:center;font-size:8px;color:#444'>"
                + esc(pied.isEmpty() ? societeNom : pied) + "<br/>Document confidentiel — édité le "
                + (generatedAt == null ? "—" : generatedAt.format(STAMP)) + "</div></div>";
    }

    // ───────────────────────────── Formats ─────────────────────────────

    private void summary(StringBuilder sb, JsonNode t) {
        if (t.isMissingNode()) return;
        sb.append("<table class='kpis'><tr>");
        kpi(sb, "Patients", count(t.path("patients")));
        kpi(sb, "Séances", num(t.path("seances")));
        kpi(sb, "Factures", num(t.path("factures")));
        kpi(sb, "CA HT", money(t.path("caHt")));
        kpi(sb, "CA TTC", money(t.path("caTtc")));
        kpi(sb, "Encaissé", money(t.path("encaisse")));
        kpi(sb, "Reste à recouvrer", money(t.path("resteARecouvrer")));
        kpi(sb, "Taux d'encaissement", pct(t.path("tauxEncaissement")));
        sb.append("</tr></table>");
    }

    private void alerts(StringBuilder sb, JsonNode alerts) {
        if (alerts.isMissingNode() || alerts.size() == 0) return;
        sb.append("<h2>Alertes</h2><div class='alerts'>");
        for (JsonNode a : alerts) {
            boolean critical = "CRITICAL".equals(text(a.path("severity")));
            sb.append("<div class='alert").append(critical ? " critical" : "").append("'><b>")
                    .append(esc(text(a.path("centre")))).append("</b> — ").append(esc(alertLabel(text(a.path("code")))))
                    .append(a.path("valeur").isNull() || a.path("valeur").isMissingNode() ? "" : " (" + esc(text(a.path("valeur"))) + ")")
                    .append("</div>");
        }
        sb.append("</div>");
    }

    private void finances(StringBuilder sb, JsonNode overview) {
        JsonNode centres = overview.path("centres");
        if (centres.isMissingNode()) return;
        double max = 0;
        for (JsonNode c : centres) max = Math.max(max, c.path("caTtc").asDouble());
        sb.append("<h2>Finances et activité par centre</h2><table><tr><th>Centre</th><th>Patients</th><th>Dont sous KT</th>")
                .append("<th>Séances</th><th>Factures</th><th>CA HT</th><th>CA TTC</th><th>Encaissé</th>")
                .append("<th>Reste à recouvrer</th><th>Taux</th><th style='width:14%'>Part du CA</th></tr>");
        for (JsonNode c : centres) {
            sb.append("<tr><td>").append(esc(text(c.path("nom")))).append("</td><td>").append(count(c.path("patients")))
                    .append("</td><td>").append(count(c.path("patientsSousKt"))).append("</td><td>").append(num(c.path("seances")))
                    .append("</td><td>").append(num(c.path("factures"))).append("</td><td>").append(money(c.path("caHt")))
                    .append("</td><td>").append(money(c.path("caTtc"))).append("</td><td>").append(money(c.path("encaisse")))
                    .append("</td><td>").append(money(c.path("resteARecouvrer"))).append("</td><td>")
                    .append(pct(c.path("tauxEncaissement"))).append("</td><td>").append(bar(c.path("caTtc").asDouble(), max))
                    .append("</td></tr>");
        }
        JsonNode t = overview.path("totaux");
        sb.append("<tr class='total'><td>Total société</td><td>").append(count(t.path("patients"))).append("</td><td>")
                .append(count(t.path("patientsSousKt"))).append("</td><td>").append(num(t.path("seances"))).append("</td><td>")
                .append(num(t.path("factures"))).append("</td><td>").append(money(t.path("caHt"))).append("</td><td>")
                .append(money(t.path("caTtc"))).append("</td><td>").append(money(t.path("encaisse"))).append("</td><td>")
                .append(money(t.path("resteARecouvrer"))).append("</td><td>").append(pct(t.path("tauxEncaissement")))
                .append("</td><td></td></tr></table>");
    }

    private void monthly(StringBuilder sb, JsonNode points) {
        if (points.isMissingNode() || points.size() == 0) return;
        Map<String, double[]> byMonth = new LinkedHashMap<>();
        for (JsonNode p : points) {
            double[] v = byMonth.computeIfAbsent(text(p.path("mois")), k -> new double[3]);
            v[0] += p.path("seances").asDouble();
            v[1] += p.path("caHt").asDouble();
            v[2] += p.path("caTtc").asDouble();
        }
        double max = byMonth.values().stream().mapToDouble(v -> v[2]).max().orElse(0);
        sb.append("<h2>Évolution mensuelle</h2><table><tr><th>Mois</th><th>Séances</th><th>CA HT</th><th>CA TTC</th>")
                .append("<th style='width:35%'>CA TTC</th></tr>");
        byMonth.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                sb.append("<tr><td>").append(esc(e.getKey())).append("</td><td>").append(Math.round(e.getValue()[0]))
                        .append("</td><td>").append(money(e.getValue()[1])).append("</td><td>").append(money(e.getValue()[2]))
                        .append("</td><td>").append(bar(e.getValue()[2], max)).append("</td></tr>"));
        sb.append("</table>");
    }

    private void clinical(StringBuilder sb, JsonNode ind) {
        JsonNode centres = ind.path("centres");
        if (centres.isMissingNode()) return;
        sb.append("<h2>Qualité des soins — part des patients dans la cible KDIGO</h2><table><tr><th>Centre</th>")
                .append("<th>Kt/V</th><th>Hb</th><th>Phosphore</th><th>PTH</th><th>Albumine</th><th>AgHBs +</th>")
                .append("<th>Anti-VHC +</th><th>VIH +</th><th>Traitement en retard</th><th>Attente greffe</th>")
                .append("<th>Bilans greffe en cours</th><th>Greffes (période)</th></tr>");
        for (JsonNode c : centres) clinicalRow(sb, c, text(c.path("nom")), false);
        clinicalRow(sb, ind.path("totaux"), "Total société", true);
        sb.append("</table>");
    }

    private void clinicalRow(StringBuilder sb, JsonNode c, String name, boolean total) {
        JsonNode k = c.path("clinique");
        sb.append(total ? "<tr class='total'>" : "<tr>").append("<td>").append(esc(name)).append("</td>");
        for (String marker : new String[]{"ktV", "hemoglobine", "phosphore", "pth", "albumine"}) {
            sb.append("<td>").append(pct(k.path(marker).path("pctDansCible"))).append("</td>");
        }
        for (String f : new String[]{"vhbPositifs", "vhcPositifs", "vihPositifs", "patientsObservanceEnRetard",
                "greffeListeAttente", "greffeBilanEnCours", "greffesPeriode"}) {
            sb.append("<td>").append(count(k.path(f))).append("</td>");
        }
        sb.append("</tr>");
    }

    private void stock(StringBuilder sb, JsonNode ind) {
        JsonNode centres = ind.path("centres");
        if (centres.isMissingNode()) return;
        sb.append("<h2>Stock</h2><table><tr><th>Centre</th><th>Articles actifs</th><th>Sous le seuil</th>")
                .append("<th>Lots périmés</th><th>Péremption &lt; 90 j</th><th>Valeur du stock</th></tr>");
        for (JsonNode c : centres) stockRow(sb, c, text(c.path("nom")), false);
        stockRow(sb, ind.path("totaux"), "Total société", true);
        sb.append("</table>");
    }

    private void stockRow(StringBuilder sb, JsonNode c, String name, boolean total) {
        JsonNode s = c.path("stock");
        sb.append(total ? "<tr class='total'>" : "<tr>").append("<td>").append(esc(name)).append("</td><td>")
                .append(num(s.path("articlesActifs"))).append("</td><td").append(cls(s.path("articlesSousSeuil"), "crit")).append(">")
                .append(num(s.path("articlesSousSeuil"))).append("</td><td").append(cls(s.path("lotsPerimes"), "crit")).append(">")
                .append(num(s.path("lotsPerimes"))).append("</td><td").append(cls(s.path("lotsPeremptionProche"), "warn")).append(">")
                .append(num(s.path("lotsPeremptionProche"))).append("</td><td>").append(money(s.path("valeurStock")))
                .append("</td></tr>");
    }

    private void breakdown(StringBuilder sb, JsonNode b) {
        if (b.isMissingNode() || b.isNull()) {
            return; // instantané antérieur aux répartitions : rien à afficher
        }
        sb.append("<h2>Répartition des patients par sexe et par âge</h2><table class='two'><tr><td>");
        sb.append("<table><tr><th>Centre</th><th>Hommes</th><th>Femmes</th><th>Autre / inconnu</th></tr>");
        for (JsonNode r : b.path("sexe")) {
            sb.append("<tr><td>").append(esc(text(r.path("nom")))).append("</td><td>").append(count(r.path("masculin")))
                    .append("</td><td>").append(count(r.path("feminin"))).append("</td><td>").append(count(r.path("autre")))
                    .append("</td></tr>");
        }
        sb.append("</table></td><td><table><tr><th>Centre</th>");
        AGE_LABELS.values().forEach(l -> sb.append("<th>").append(esc(l)).append("</th>"));
        sb.append("</tr>");
        for (JsonNode r : b.path("ages")) {
            sb.append("<tr><td>").append(esc(text(r.path("nom")))).append("</td>");
            for (String code : AGE_LABELS.keySet()) {
                String cell = "—";
                for (JsonNode t : r.path("tranches")) {
                    if (code.equals(text(t.path("code")))) cell = count(t.path("count"));
                }
                sb.append("<td>").append(cell).append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</table></td></tr></table>");

        caisses(sb, b);
        anemie(sb, b.path("anemie"));
    }

    private void caisses(StringBuilder sb, JsonNode b) {
        JsonNode totaux = b.path("caisseTotaux");
        if (totaux.isMissingNode() || totaux.size() == 0) return;
        List<String[]> centres = new ArrayList<>();
        for (JsonNode s : b.path("sexe")) centres.add(new String[]{text(s.path("centerId")), text(s.path("nom"))});
        sb.append("<h2>Caisses d'assurance par centre</h2>");
        for (String[] metric : new String[][]{{"patients", "Patients"}, {"seances", "Séances"}, {"caHt", "CA HT"}}) {
            sb.append("<table><tr><th>").append(esc(metric[1])).append(" — caisse</th>");
            centres.forEach(c -> sb.append("<th>").append(esc(c[1])).append("</th>"));
            sb.append("<th>Total société</th></tr>");
            for (JsonNode t : totaux) {
                String code = text(t.path("caisseCode"));
                String nom = t.path("caisse").isNull() || t.path("caisse").isMissingNode() ? "" : text(t.path("caisse"));
                sb.append("<tr><td>").append(esc(nom.isEmpty() ? "Caisse non renseignée" : nom)).append("</td>");
                for (String[] c : centres) {
                    String cell = metric[0].equals("caHt") ? money(BigDecimal.ZERO) : "0";
                    for (JsonNode row : b.path("caisses")) {
                        if (c[0].equals(text(row.path("centerId"))) && code.equals(text(row.path("caisseCode")).replace("—", ""))) {
                            cell = metricCell(metric[0], row.path(metric[0]));
                        }
                    }
                    sb.append("<td>").append(cell).append("</td>");
                }
                sb.append("<td><b>").append(metricCell(metric[0], t.path(metric[0]))).append("</b></td></tr>");
            }
            sb.append("</table>");
        }
    }

    private String metricCell(String metric, JsonNode value) {
        return switch (metric) {
            case "patients" -> count(value);
            case "caHt" -> money(value);
            default -> num(value);
        };
    }

    private void anemie(StringBuilder sb, JsonNode rows) {
        if (rows.isMissingNode() || rows.size() == 0) return;
        sb.append("<h2>Traitement de l'anémie par centre</h2><table><tr><th>Centre</th><th>Sous EPO (prescrit)</th>")
                .append("<th>Sous fer (prescrit)</th><th>Patients traités EPO</th><th>Patients traités fer</th>")
                .append("<th>Administrations EPO</th><th>Administrations fer</th><th>Non administrées</th>")
                .append("<th>Taux d'administration</th></tr>");
        for (JsonNode r : rows) {
            sb.append("<tr><td>").append(esc(text(r.path("nom")))).append("</td><td>").append(count(r.path("patientsSousEpo")))
                    .append("</td><td>").append(count(r.path("patientsSousFer"))).append("</td><td>")
                    .append(count(r.path("patientsEpo"))).append("</td><td>").append(count(r.path("patientsFer")))
                    .append("</td><td>").append(num(r.path("administreesEpo"))).append("</td><td>")
                    .append(num(r.path("administreesFer"))).append("</td><td").append(cls(r.path("nonAdministrees"), "warn"))
                    .append(">").append(num(r.path("nonAdministrees"))).append("</td><td>")
                    .append(pct(r.path("tauxAdministration"))).append("</td></tr>");
        }
        sb.append("</table>");
    }
}
