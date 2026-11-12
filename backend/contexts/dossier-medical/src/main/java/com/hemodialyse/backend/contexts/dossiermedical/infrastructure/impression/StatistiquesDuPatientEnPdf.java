package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.impression;

import com.hemodialyse.backend.contexts.dossiermedical.application.ImpressionDesStatistiquesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.StatistiquesMedicales;
import com.hemodialyse.backend.contexts.dossiermedical.application.StatistiquesParamedicales;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PeriodeDeStatistiques;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.PapierEnTeteDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Mise en page PDF du rapport de statistiques d'un patient (PL4) : construction d'un HTML puis rendu openhtmltopdf, HTML identique a celui de
 * l'ancien {@code PatientStatsQueryService.exportPdf} de la plateforme. L'en-tete et le pied de page d'identite viennent du port
 * {@link PapierEnTeteDuCentre} ; l'heure « Genere le » vient de l'horloge injectee.
 */
public class StatistiquesDuPatientEnPdf implements ImpressionDesStatistiquesDuPatient {

    private final PapierEnTeteDuCentre papierEnTete;
    private final Clock horloge;

    public StatistiquesDuPatientEnPdf(PapierEnTeteDuCentre papierEnTete, Clock horloge) {
        this.papierEnTete = papierEnTete;
        this.horloge = horloge;
    }

    @Override
    public byte[] enPdf(CentreId centre, UUID patient, PeriodeDeStatistiques periode, StatistiquesParamedicales paramedicales,
                        StatistiquesMedicales medicales) {
        String html = papierEnTete.habiller(html(centre.valeur(), patient, periode, paramedicales, medicales), centre.valeur());
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de generer le PDF stats", e);
        }
    }

    private String html(UUID centre, UUID patient, PeriodeDeStatistiques periode, StatistiquesParamedicales paramedicales,
                        StatistiquesMedicales medicales) {
        String du = periode.du() != null ? periode.du().toString() : "-";
        String au = periode.au() != null ? periode.au().toString() : "-";
        String genereLe = LocalDateTime.now(horloge).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset='UTF-8'/><style>")
                .append("body{font-family:Arial,sans-serif;font-size:12px;color:#111;margin:24px;}")
                .append("h1{font-size:20px;margin:0 0 8px 0;}h2{font-size:16px;margin:20px 0 8px 0;}")
                .append(".muted{color:#666;font-size:11px;margin-bottom:12px;}")
                .append(".kpis{display:block;margin:8px 0 12px 0;}")
                .append(".kpi{margin:3px 0;}")
                .append("table{width:100%;border-collapse:collapse;margin-top:8px;}")
                .append("th,td{border:1px solid #ddd;padding:6px;text-align:left;}")
                .append("th{background:#f5f7fb;}")
                .append("</style></head><body>");

        sb.append("<h1>Rapport statistiques patient</h1>")
                .append("<div class='muted'>Centre: ").append(echapper(centre.toString()))
                .append(" | Patient: ").append(echapper(patient.toString()))
                .append(" | Periode: ").append(echapper(du)).append(" -> ").append(echapper(au))
                .append(" | Genere le: ").append(echapper(genereLe))
                .append("</div>");

        sb.append("<h2>Statistiques paramedicales</h2>")
                .append("<div class='kpis'>")
                .append("<div class='kpi'>Seances: ").append(paramedicales.nombreDeSeances()).append("</div>")
                .append("<div class='kpi'>Poids avant moyen (kg): ").append(nombre(paramedicales.moyennePoidsAvant())).append("</div>")
                .append("<div class='kpi'>Poids apres moyen (kg): ").append(nombre(paramedicales.moyennePoidsApres())).append("</div>")
                .append("<div class='kpi'>UF reelle moyenne (mL): ").append(nombre(paramedicales.moyenneUltrafiltration())).append("</div>")
                .append("</div>");
        tableauDesPoids(sb, paramedicales.evolutionDuPoids());

        sb.append("<h2>Statistiques medicales</h2>")
                .append("<div class='kpis'>")
                .append("<div class='kpi'>Hb moyenne (g/dL): ").append(nombre(medicales.hemoglobine())).append("</div>")
                .append("<div class='kpi'>Kt/V moyen: ").append(nombre(medicales.ktV())).append("</div>")
                .append("<div class='kpi'>Ferritine moyenne (ng/mL): ").append(nombre(medicales.ferritine())).append("</div>")
                .append("</div>");
        tableauDesBilans(sb, medicales.tendanceDesBilans());

        sb.append("</body></html>");
        return sb.toString();
    }

    private void tableauDesPoids(StringBuilder sb, List<Map<String, Object>> lignes) {
        sb.append("<table><thead><tr><th>Date seance</th><th>Poids avant</th><th>Poids apres</th><th>Poids sec cible</th></tr></thead><tbody>");
        if (lignes.isEmpty()) {
            sb.append("<tr><td colspan='4'>Aucune donnee</td></tr>");
        } else {
            for (Map<String, Object> ligne : lignes) {
                sb.append("<tr><td>").append(echapper(cellule(ligne, "date_seance", "DATE_SEANCE")))
                        .append("</td><td>").append(echapper(cellule(ligne, "poids_avant_kg", "POIDS_AVANT_KG")))
                        .append("</td><td>").append(echapper(cellule(ligne, "poids_apres_kg", "POIDS_APRES_KG")))
                        .append("</td><td>").append(echapper(cellule(ligne, "poids_sec_cible_kg", "POIDS_SEC_CIBLE_KG")))
                        .append("</td></tr>");
            }
        }
        sb.append("</tbody></table>");
    }

    private void tableauDesBilans(StringBuilder sb, List<Map<String, Object>> lignes) {
        sb.append("<table><thead><tr><th>Date prelevement</th><th>Hb</th><th>Kt/V</th><th>Ferritine</th></tr></thead><tbody>");
        if (lignes.isEmpty()) {
            sb.append("<tr><td colspan='4'>Aucune donnee</td></tr>");
        } else {
            for (Map<String, Object> ligne : lignes) {
                sb.append("<tr><td>").append(echapper(cellule(ligne, "date_prelevement", "DATE_PRELEVEMENT")))
                        .append("</td><td>").append(echapper(cellule(ligne, "hb_g_dl", "HB_G_DL")))
                        .append("</td><td>").append(echapper(cellule(ligne, "kt_v_mensuel", "KT_V_MENSUEL")))
                        .append("</td><td>").append(echapper(cellule(ligne, "ferritine_ng_ml", "FERRITINE_NG_ML")))
                        .append("</td></tr>");
            }
        }
        sb.append("</tbody></table>");
    }

    private static String cellule(Map<String, Object> ligne, String cleEnMinuscules, String cleEnMajuscules) {
        Object valeur = ligne.get(cleEnMinuscules);
        if (valeur == null) valeur = ligne.get(cleEnMajuscules);
        return valeur == null ? "" : String.valueOf(valeur);
    }

    private static String nombre(Double valeur) {
        return valeur == null ? "0" : String.format(Locale.ROOT, "%.2f", valeur);
    }

    private static String echapper(String brut) {
        if (brut == null) return "";
        return brut.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
