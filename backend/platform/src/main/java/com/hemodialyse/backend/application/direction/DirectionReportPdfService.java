package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DirectionSnapshotService.Snapshot;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * Rapport mensuel de la direction (PDF) construit à partir d'un instantané figé : synthèse de la société, tableau
 * par centre (finances, qualité des soins, stock) et alertes. Agrégats anonymes uniquement ; les valeurs masquées
 * par l'anonymat restent masquées.
 */
@Service
public class DirectionReportPdfService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static String label(String code) {
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

    private static String text(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() ? "—" : n.asString();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public byte[] pdf(Snapshot snapshot) {
        String html = html(snapshot);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de générer le rapport PDF", e);
        }
    }

    String html(Snapshot s) {
        JsonNode overview = s.overview();
        JsonNode ind = s.indicators();
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset='UTF-8'/><style>")
                .append("@page{size:A4 landscape;margin:16mm}")
                .append("body{font-family:Arial,sans-serif;font-size:10px;color:#111}")
                .append("h1{font-size:18px;margin:0 0 2px}h2{font-size:13px;margin:16px 0 6px}")
                .append(".muted{color:#555}table{width:100%;border-collapse:collapse;margin-bottom:8px}")
                .append("th,td{border:1px solid #ccc;padding:4px 6px;text-align:right}")
                .append("th:first-child,td:first-child{text-align:left}th{background:#eef2f5}")
                .append("tr.total td{font-weight:bold;background:#f6f8fa}")
                .append(".crit{color:#c62828;font-weight:bold}.warn{color:#ed6c02}")
                .append("</style></head><body>");
        sb.append("<h1>Rapport mensuel — ").append(esc(text(overview.path("societeNom")))).append("</h1>");
        sb.append("<div class='muted'>Période : ").append(esc(s.mois())).append(" (")
                .append(esc(text(overview.path("from")))).append(" au ").append(esc(text(overview.path("to"))))
                .append(") — figé le ").append(s.generatedAt() == null ? "—" : s.generatedAt().format(STAMP))
                .append(" — agrégats anonymes (seuil d'anonymat : ")
                .append(esc(text(overview.path("seuilAnonymat")))).append(")</div>");

        sb.append("<h2>Finances et activité</h2><table><tr><th>Centre</th><th>Patients</th><th>Séances</th>")
                .append("<th>Factures</th><th>CA TTC</th><th>Encaissé</th><th>Reste à recouvrer</th><th>Taux</th></tr>");
        for (JsonNode c : overview.path("centres")) {
            financeRow(sb, c, c.path("nom"), false);
        }
        financeRow(sb, overview.path("totaux"), null, true);
        sb.append("</table>");

        sb.append("<h2>Qualité des soins (cibles KDIGO, part de patients dans la cible)</h2><table>")
                .append("<tr><th>Centre</th><th>Kt/V</th><th>Hb</th><th>Phosphore</th><th>PTH</th><th>Albumine</th>")
                .append("<th>AgHBs+</th><th>Anti-VHC+</th><th>VIH+</th><th>Traitement en retard</th>")
                .append("<th>Attente greffe</th><th>Greffes</th></tr>");
        for (JsonNode c : ind.path("centres")) {
            clinicalRow(sb, c, c.path("nom"), false);
        }
        clinicalRow(sb, ind.path("totaux"), null, true);
        sb.append("</table>");

        sb.append("<h2>Stock</h2><table><tr><th>Centre</th><th>Articles actifs</th><th>Sous le seuil</th>")
                .append("<th>Lots périmés</th><th>Péremption &lt; 90 j</th><th>Valeur</th></tr>");
        for (JsonNode c : ind.path("centres")) {
            stockRow(sb, c, c.path("nom"), false);
        }
        stockRow(sb, ind.path("totaux"), null, true);
        sb.append("</table>");

        JsonNode alerts = ind.path("alertes");
        if (alerts.size() > 0) {
            sb.append("<h2>Alertes</h2><table><tr><th>Centre</th><th>Alerte</th><th>Niveau</th><th>Valeur</th></tr>");
            for (JsonNode a : alerts) {
                boolean critical = "CRITICAL".equals(text(a.path("severity")));
                sb.append("<tr><td>").append(esc(text(a.path("centre")))).append("</td><td>")
                        .append(esc(label(text(a.path("code"))))).append("</td><td class='")
                        .append(critical ? "crit" : "warn").append("'>").append(critical ? "Critique" : "Attention")
                        .append("</td><td>").append(esc(text(a.path("valeur")))).append("</td></tr>");
            }
            sb.append("</table>");
        }
        return sb.append("</body></html>").toString();
    }

    private void financeRow(StringBuilder sb, JsonNode c, JsonNode name, boolean total) {
        sb.append(total ? "<tr class='total'>" : "<tr>").append("<td>").append(total ? "Total société" : esc(text(name)))
                .append("</td><td>").append(count(c.path("patients"))).append("</td><td>")
                .append(esc(text(c.path("seances")))).append("</td><td>").append(esc(text(c.path("factures"))))
                .append("</td><td>").append(esc(text(c.path("caTtc")))).append("</td><td>")
                .append(esc(text(c.path("encaisse")))).append("</td><td>").append(esc(text(c.path("resteARecouvrer"))))
                .append("</td><td>").append(pct(c.path("tauxEncaissement"))).append("</td></tr>");
    }

    private void clinicalRow(StringBuilder sb, JsonNode c, JsonNode name, boolean total) {
        JsonNode k = c.path("clinique");
        sb.append(total ? "<tr class='total'>" : "<tr>").append("<td>").append(total ? "Total société" : esc(text(name)))
                .append("</td>");
        for (String marker : new String[]{"ktV", "hemoglobine", "phosphore", "pth", "albumine"}) {
            sb.append("<td>").append(pct(k.path(marker).path("pctDansCible"))).append("</td>");
        }
        for (String field : new String[]{"vhbPositifs", "vhcPositifs", "vihPositifs", "patientsObservanceEnRetard",
                "greffeListeAttente", "greffesPeriode"}) {
            sb.append("<td>").append(count(k.path(field))).append("</td>");
        }
        sb.append("</tr>");
    }

    private void stockRow(StringBuilder sb, JsonNode c, JsonNode name, boolean total) {
        JsonNode st = c.path("stock");
        sb.append(total ? "<tr class='total'>" : "<tr>").append("<td>").append(total ? "Total société" : esc(text(name)))
                .append("</td><td>").append(esc(text(st.path("articlesActifs")))).append("</td><td>")
                .append(esc(text(st.path("articlesSousSeuil")))).append("</td><td>")
                .append(esc(text(st.path("lotsPerimes")))).append("</td><td>")
                .append(esc(text(st.path("lotsPeremptionProche")))).append("</td><td>")
                .append(esc(text(st.path("valeurStock")))).append("</td></tr>");
    }

    /**
     * Effectif : masqué (« &lt; seuil ») quand le serveur l'a masqué au moment de la capture.
     */
    private String count(JsonNode n) {
        return n.isMissingNode() || n.isNull() ? "&lt; seuil" : esc(n.asString());
    }

    private String pct(JsonNode n) {
        return n.isMissingNode() || n.isNull() ? "—" : esc(n.asString()) + " %";
    }
}
