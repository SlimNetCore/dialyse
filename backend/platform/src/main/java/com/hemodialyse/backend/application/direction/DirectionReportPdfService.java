package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.SocieteLetterheadPort.Letterhead;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.InstantanesMensuelsDeLaDirection.Instantane;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Alerte;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Caisses;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Finances;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.LigneClinique;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.LigneDeCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.LigneFinanciere;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.LigneStock;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Mois;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Rapport;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Repartitions;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Synthese;
import com.hemodialyse.backend.infrastructure.acl.pilotagedirection.RapportDUnInstantane;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Rendu du rapport de direction imprimable (PDF, A4 paysage) : en-tête et pied de page de la société (logo, coordonnées, mentions légales,
 * pied de page personnalisé, numéro de page) sur chaque page, puis l'ensemble des statistiques du tableau de bord — synthèse, finances,
 * évolution mensuelle, qualité des soins, stock, alertes, répartitions par sexe, âge et caisse d'assurance, traitement de l'anémie.
 * <p>
 * PD4 (ADR 0102) : le MODÈLE du rapport (sections, totaux, évolution mensuelle, matrice des caisses, masquage) est constitué par le contexte
 * Pilotage direction ({@link RapportDeSyntheseDeLaDirection}) ; cette classe ne fait plus que la MISE EN FORME (« &lt; seuil », « — »,
 * montants, barres), le HTML puis le PDF (openhtmltopdf 1.0.10) et l'en-tête de la société ({@link SocieteLetterheadPort}). Deux sources :
 * un instantané mensuel figé, ou les données en direct d'une période choisie.
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
    private final RapportDeSyntheseDeLaDirection rapports;

    public DirectionReportPdfService(SocieteLetterheadPort letterheads, RapportDeSyntheseDeLaDirection rapports) {
        this.letterheads = letterheads;
        this.rapports = rapports;
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

    // ───────────────────────────── Mise en forme ─────────────────────────────

    private static String text(Object n) {
        return n == null ? "—" : n.toString();
    }

    /**
     * Effectif de patients : masqué (« &lt; seuil ») quand l'anonymat l'impose.
     */
    private static String count(Long n) {
        return n == null ? "&lt; seuil" : esc(n.toString());
    }

    private static String num(Object n) {
        return n == null ? "—" : esc(n.toString());
    }

    private static String pct(BigDecimal n) {
        return n == null ? "—" : esc(n.toString()) + " %";
    }

    private static String money(BigDecimal n) {
        return n == null ? "—" : money(n.doubleValue());
    }

    private static String money(double value) {
        return String.format(Locale.FRANCE, "%,.0f DA", value).replace(' ', ' ').replace(' ', ' ');
    }

    private static String bar(double value, double max) {
        int width = max <= 0 ? 0 : (int) Math.round(Math.min(100, value * 100 / max));
        return "<div class='bar'><div style='width:" + width + "%'></div></div>";
    }

    private static String cls(Number n, String cssClass) {
        return n != null && n.doubleValue() > 0 ? " class='" + cssClass + "'" : "";
    }

    private static String cls(BigDecimal n, String cssClass) {
        return n != null && n.doubleValue() > 0 ? " class='" + cssClass + "'" : "";
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    // ───────────────────────────── Document ─────────────────────────────

    /**
     * Rapport d'un mois figé.
     */
    public byte[] pdf(UUID societeId, Instantane snapshot) {
        Rapport rapport = rapports.constituer(RapportDUnInstantane.source("Rapport mensuel — " + snapshot.mois(),
                snapshot.generatedAt(), snapshot.overview(), snapshot.indicators(), snapshot.breakdown()));
        return render(html(societeId, rapport));
    }

    /**
     * Rapport en direct sur la période choisie (par défaut : depuis le 1er janvier).
     */
    public byte[] pdf(UUID societeId, LocalDate from, LocalDate to) {
        return render(html(societeId, rapports.enDirect(societeId, from, to)));
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

    String html(UUID societeId, Rapport rapport) {
        Optional<Letterhead> letterhead = letterheads.forSociete(societeId);
        String societeNom = letterhead.map(Letterhead::nom).filter(n -> !n.isBlank()).orElse(text(rapport.societeNom()));
        String period = text(rapport.debut()) + " au " + text(rapport.fin());
        OffsetDateTime generatedAt = rapport.genereLe();

        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset='UTF-8'/><style>").append(css()).append("</style></head><body>");
        sb.append(letterheadHtml(letterhead, societeNom, rapport.titre(), period));
        sb.append(footerHtml(letterhead, societeNom, generatedAt));

        sb.append("<h1>").append(esc(rapport.titre())).append("</h1>");
        sb.append("<div class='muted'>").append(esc(societeNom)).append(" — période ").append(esc(period))
                .append(" — données au ").append(generatedAt == null ? "—" : generatedAt.format(STAMP))
                .append(" — agrégats anonymes (seuil d'anonymat : ").append(esc(text(rapport.seuilAnonymat())))
                .append(")</div>");

        summary(sb, rapport.synthese());
        alerts(sb, rapport.alertes());
        finances(sb, rapport.finances());
        monthly(sb, rapport.evolution());
        clinical(sb, rapport);
        stock(sb, rapport);
        breakdown(sb, rapport.repartitions());
        sb.append("<p class='note'>Les effectifs de moins de ")
                .append(esc(text(rapport.seuilAnonymat())))
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

    // ───────────────────────────── Sections ─────────────────────────────

    private void summary(StringBuilder sb, Synthese t) {
        if (t == null) return;
        sb.append("<table class='kpis'><tr>");
        kpi(sb, "Patients", count(t.patients()));
        kpi(sb, "Séances", num(t.seances()));
        kpi(sb, "Factures", num(t.factures()));
        kpi(sb, "CA HT", money(t.caHt()));
        kpi(sb, "CA TTC", money(t.caTtc()));
        kpi(sb, "Encaissé", money(t.encaisse()));
        kpi(sb, "Reste à recouvrer", money(t.resteARecouvrer()));
        kpi(sb, "Taux d'encaissement", pct(t.tauxEncaissement()));
        sb.append("</tr></table>");
    }

    private void alerts(StringBuilder sb, List<Alerte> alerts) {
        if (alerts.isEmpty()) return;
        sb.append("<h2>Alertes</h2><div class='alerts'>");
        for (Alerte a : alerts) {
            sb.append("<div class='alert").append(a.critique() ? " critical" : "").append("'><b>")
                    .append(esc(text(a.centre()))).append("</b> — ").append(esc(a.libelle()))
                    .append(a.valeur() == null ? "" : " (" + esc(text(a.valeur())) + ")")
                    .append("</div>");
        }
        sb.append("</div>");
    }

    private void finances(StringBuilder sb, Finances finances) {
        if (finances == null) return;
        double max = 0;
        for (LigneFinanciere c : finances.centres()) max = Math.max(max, c.caTtc() == null ? 0 : c.caTtc().doubleValue());
        sb.append("<h2>Finances et activité par centre</h2><table><tr><th>Centre</th><th>Patients</th><th>Dont sous KT</th>")
                .append("<th>Séances</th><th>Factures</th><th>CA HT</th><th>CA TTC</th><th>Encaissé</th>")
                .append("<th>Reste à recouvrer</th><th>Taux</th><th style='width:14%'>Part du CA</th></tr>");
        for (LigneFinanciere c : finances.centres()) {
            sb.append("<tr><td>").append(esc(text(c.nom()))).append("</td><td>").append(count(c.patients()))
                    .append("</td><td>").append(count(c.patientsSousKt())).append("</td><td>").append(num(c.seances()))
                    .append("</td><td>").append(num(c.factures())).append("</td><td>").append(money(c.caHt()))
                    .append("</td><td>").append(money(c.caTtc())).append("</td><td>").append(money(c.encaisse()))
                    .append("</td><td>").append(money(c.resteARecouvrer())).append("</td><td>")
                    .append(pct(c.tauxEncaissement())).append("</td><td>")
                    .append(bar(c.caTtc() == null ? 0 : c.caTtc().doubleValue(), max)).append("</td></tr>");
        }
        LigneFinanciere t = finances.total();
        sb.append("<tr class='total'><td>Total société</td><td>").append(count(t.patients())).append("</td><td>")
                .append(count(t.patientsSousKt())).append("</td><td>").append(num(t.seances())).append("</td><td>")
                .append(num(t.factures())).append("</td><td>").append(money(t.caHt())).append("</td><td>")
                .append(money(t.caTtc())).append("</td><td>").append(money(t.encaisse())).append("</td><td>")
                .append(money(t.resteARecouvrer())).append("</td><td>").append(pct(t.tauxEncaissement()))
                .append("</td><td></td></tr></table>");
    }

    private void monthly(StringBuilder sb, List<Mois> mois) {
        if (mois.isEmpty()) return;
        double max = mois.stream().mapToDouble(Mois::caTtc).max().orElse(0);
        sb.append("<h2>Évolution mensuelle</h2><table><tr><th>Mois</th><th>Séances</th><th>CA HT</th><th>CA TTC</th>")
                .append("<th style='width:35%'>CA TTC</th></tr>");
        mois.forEach(m ->
                sb.append("<tr><td>").append(esc(m.mois())).append("</td><td>").append(m.seances())
                        .append("</td><td>").append(money(m.caHt())).append("</td><td>").append(money(m.caTtc()))
                        .append("</td><td>").append(bar(m.caTtc(), max)).append("</td></tr>"));
        sb.append("</table>");
    }

    private void clinical(StringBuilder sb, Rapport rapport) {
        if (rapport.qualite() == null) return;
        sb.append("<h2>Qualité des soins — part des patients dans la cible KDIGO</h2><table><tr><th>Centre</th>")
                .append("<th>Kt/V</th><th>Hb</th><th>Phosphore</th><th>PTH</th><th>Albumine</th><th>AgHBs +</th>")
                .append("<th>Anti-VHC +</th><th>VIH +</th><th>Traitement en retard</th><th>Attente greffe</th>")
                .append("<th>Bilans greffe en cours</th><th>Greffes (période)</th></tr>");
        for (LigneClinique c : rapport.qualite().centres()) clinicalRow(sb, c, text(c.nom()), false);
        clinicalRow(sb, rapport.qualite().total(), "Total société", true);
        sb.append("</table>");
    }

    private void clinicalRow(StringBuilder sb, LigneClinique k, String name, boolean total) {
        sb.append(total ? "<tr class='total'>" : "<tr>").append("<td>").append(esc(name)).append("</td>");
        for (BigDecimal cible : new BigDecimal[]{k.ktV(), k.hemoglobine(), k.phosphore(), k.pth(), k.albumine()}) {
            sb.append("<td>").append(pct(cible)).append("</td>");
        }
        for (Long f : new Long[]{k.vhbPositifs(), k.vhcPositifs(), k.vihPositifs(), k.patientsObservanceEnRetard(),
                k.greffeListeAttente(), k.greffeBilanEnCours(), k.greffesPeriode()}) {
            sb.append("<td>").append(count(f)).append("</td>");
        }
        sb.append("</tr>");
    }

    private void stock(StringBuilder sb, Rapport rapport) {
        if (rapport.stock() == null) return;
        sb.append("<h2>Stock</h2><table><tr><th>Centre</th><th>Articles actifs</th><th>Sous le seuil</th>")
                .append("<th>Lots périmés</th><th>Péremption &lt; 90 j</th><th>Valeur du stock</th></tr>");
        for (LigneStock c : rapport.stock().centres()) stockRow(sb, c, text(c.nom()), false);
        stockRow(sb, rapport.stock().total(), "Total société", true);
        sb.append("</table>");
    }

    private void stockRow(StringBuilder sb, LigneStock s, String name, boolean total) {
        sb.append(total ? "<tr class='total'>" : "<tr>").append("<td>").append(esc(name)).append("</td><td>")
                .append(num(s.articlesActifs())).append("</td><td").append(cls(s.articlesSousSeuil(), "crit")).append(">")
                .append(num(s.articlesSousSeuil())).append("</td><td").append(cls(s.lotsPerimes(), "crit")).append(">")
                .append(num(s.lotsPerimes())).append("</td><td").append(cls(s.lotsPeremptionProche(), "warn")).append(">")
                .append(num(s.lotsPeremptionProche())).append("</td><td>").append(money(s.valeurStock()))
                .append("</td></tr>");
    }

    private void breakdown(StringBuilder sb, Repartitions b) {
        if (b == null) {
            return; // instantané antérieur aux répartitions : rien à afficher
        }
        sb.append("<h2>Répartition des patients par sexe et par âge</h2><table class='two'><tr><td>");
        sb.append("<table><tr><th>Centre</th><th>Hommes</th><th>Femmes</th><th>Autre / inconnu</th></tr>");
        b.sexe().forEach(r -> sb.append("<tr><td>").append(esc(text(r.nom()))).append("</td><td>").append(count(r.masculin()))
                .append("</td><td>").append(count(r.feminin())).append("</td><td>").append(count(r.autre()))
                .append("</td></tr>"));
        sb.append("</table></td><td><table><tr><th>Centre</th>");
        AGE_LABELS.values().forEach(l -> sb.append("<th>").append(esc(l)).append("</th>"));
        sb.append("</tr>");
        b.ages().forEach(r -> {
            sb.append("<tr><td>").append(esc(text(r.nom()))).append("</td>");
            for (String code : AGE_LABELS.keySet()) {
                sb.append("<td>").append(r.tranches().containsKey(code) ? count(r.tranches().get(code)) : "—").append("</td>");
            }
            sb.append("</tr>");
        });
        sb.append("</table></td></tr></table>");

        caisses(sb, b.caisses());
        anemie(sb, b);
    }

    private void caisses(StringBuilder sb, Caisses caisses) {
        if (caisses == null) return;
        sb.append("<h2>Caisses d'assurance par centre</h2>");
        for (String metric : new String[]{"Patients", "Séances", "CA HT"}) {
            sb.append("<table><tr><th>").append(esc(metric)).append(" — caisse</th>");
            caisses.centres().forEach(c -> sb.append("<th>").append(esc(c)).append("</th>"));
            sb.append("<th>Total société</th></tr>");
            for (LigneDeCaisse ligne : caisses.lignes()) {
                sb.append("<tr><td>").append(esc(ligne.caisse())).append("</td>");
                for (int i = 0; i < caisses.centres().size(); i++) {
                    sb.append("<td>").append(switch (metric) {
                        case "Patients" -> count(ligne.patients().get(i));
                        case "CA HT" -> money(ligne.caHt().get(i));
                        default -> num(ligne.seances().get(i));
                    }).append("</td>");
                }
                sb.append("<td><b>").append(switch (metric) {
                    case "Patients" -> count(ligne.totalPatients());
                    case "CA HT" -> money(ligne.totalCaHt());
                    default -> num(ligne.totalSeances());
                }).append("</b></td></tr>");
            }
            sb.append("</table>");
        }
    }

    private void anemie(StringBuilder sb, Repartitions b) {
        if (b.anemie().isEmpty()) return;
        sb.append("<h2>Traitement de l'anémie par centre</h2><table><tr><th>Centre</th><th>Sous EPO (prescrit)</th>")
                .append("<th>Sous fer (prescrit)</th><th>Patients traités EPO</th><th>Patients traités fer</th>")
                .append("<th>Administrations EPO</th><th>Administrations fer</th><th>Non administrées</th>")
                .append("<th>Taux d'administration</th></tr>");
        b.anemie().forEach(r -> sb.append("<tr><td>").append(esc(text(r.nom()))).append("</td><td>").append(count(r.patientsSousEpo()))
                .append("</td><td>").append(count(r.patientsSousFer())).append("</td><td>")
                .append(count(r.patientsEpo())).append("</td><td>").append(count(r.patientsFer()))
                .append("</td><td>").append(num(r.administreesEpo())).append("</td><td>")
                .append(num(r.administreesFer())).append("</td><td").append(cls(r.nonAdministrees(), "warn"))
                .append(">").append(num(r.nonAdministrees())).append("</td><td>")
                .append(pct(r.tauxAdministration())).append("</td></tr>"));
        sb.append("</table>");
    }
}
