package com.hemodialyse.backend.contexts.seance.infrastructure.export;

import com.hemodialyse.backend.contexts.seance.application.ExportsDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.domain.FormatDExport;
import com.hemodialyse.backend.contexts.seance.domain.TableauDeBordDesSeances;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Fichiers d'export du tableau de bord mensuel : CSV (texte), XLSX (Apache POI, trois feuilles) et PDF (OpenHTMLtoPDF). Mêmes
 * bibliothèques, mêmes libellés et même mise en page que le legacy ; seuls les compteurs et les répartitions sont écrits (ni donnée
 * nominative, ni montant). Les libellés du XLSX sont volontairement sans accent (reproduit).
 */
public class ExportsDuTableauDeBordCsvPdfXlsx implements ExportsDuTableauDeBord {

    @Override
    public byte[] produire(FormatDExport format, TableauDeBordDesSeances tableau) {
        return switch (format) {
            case CSV -> csv(tableau).getBytes(StandardCharsets.UTF_8);
            case PDF -> pdf(tableau);
            case XLSX -> xlsx(tableau);
        };
    }

    static String csv(TableauDeBordDesSeances t) {
        StringBuilder sb = new StringBuilder();
        sb.append("year,month,expectedSeances,presenceCount,absenceCount,totalSeances\n");
        sb.append(t.mois().annee())
                .append(',').append(t.mois().mois())
                .append(',').append(t.seancesPrevues())
                .append(',').append(t.presences())
                .append(',').append(t.absences())
                .append(',').append(t.seancesTotales()).append("\n\n");
        sb.append("sexe,count\n");
        t.effectif().parSexe().forEach((k, v) -> sb.append(k).append(',').append(v).append("\n"));
        sb.append("\n");
        sb.append("ageRange,count\n");
        t.effectif().parTranche().forEach((k, v) -> sb.append(k).append(',').append(v).append("\n"));
        return sb.toString();
    }

    private static byte[] pdf(TableauDeBordDesSeances t) {
        String html = """
                <html><head><meta charset='UTF-8'/>
                <style>
                body{font-family:Arial,sans-serif;font-size:12px;color:#111;margin:20px;}
                h1{font-size:20px;margin:0 0 10px 0;} h2{font-size:15px;margin:16px 0 6px 0;}
                table{width:100%%;border-collapse:collapse;margin-top:8px;}
                th,td{border:1px solid #ddd;padding:6px;text-align:left;} th{background:#f5f7fb;}
                </style></head><body>
                <h1>Dashboard séances</h1>
                <p><strong>Période:</strong> %s-%s</p>
                <p><strong>Séances prévues:</strong> %s</p>
                <p><strong>Présences:</strong> %s</p>
                <p><strong>Absences:</strong> %s</p>
                <p><strong>Séances totales:</strong> %s</p>
                %s
                %s
                </body></html>
                """.formatted(
                t.mois().annee(),
                String.format("%02d", t.mois().mois()),
                t.seancesPrevues(),
                t.presences(),
                t.absences(),
                t.seancesTotales(),
                tableauHtml("Répartition sexe", "Sexe", t.effectif().parSexe()),
                tableauHtml("Répartition âge", "Tranche", t.effectif().parTranche()));
        try {
            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            PdfRendererBuilder constructeur = new PdfRendererBuilder();
            constructeur.useFastMode();
            constructeur.withHtmlContent(html, null);
            constructeur.toStream(sortie);
            constructeur.run();
            return sortie.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de générer le PDF dashboard séances", e);
        }
    }

    private static byte[] xlsx(TableauDeBordDesSeances t) {
        try (XSSFWorkbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            Sheet synthese = classeur.createSheet("Synthese");
            int ligne = 0;
            ligne = paire(synthese, ligne, "Annee", String.valueOf(t.mois().annee()));
            ligne = paire(synthese, ligne, "Mois", String.valueOf(t.mois().mois()));
            ligne = paire(synthese, ligne, "Seances prevues", String.valueOf(t.seancesPrevues()));
            ligne = paire(synthese, ligne, "Presences", String.valueOf(t.presences()));
            ligne = paire(synthese, ligne, "Absences", String.valueOf(t.absences()));
            paire(synthese, ligne, "Seances totales", String.valueOf(t.seancesTotales()));

            Sheet sexe = classeur.createSheet("Repartition sexe");
            repartition(sexe, "Sexe", t.effectif().parSexe());
            Sheet age = classeur.createSheet("Repartition age");
            repartition(age, "Tranche age", t.effectif().parTranche());

            synthese.autoSizeColumn(0);
            synthese.autoSizeColumn(1);
            sexe.autoSizeColumn(0);
            sexe.autoSizeColumn(1);
            age.autoSizeColumn(0);
            age.autoSizeColumn(1);

            classeur.write(sortie);
            return sortie.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de générer l'export XLSX dashboard séances", e);
        }
    }

    private static int paire(Sheet feuille, int indice, String cle, String valeur) {
        var ligne = feuille.createRow(indice);
        ligne.createCell(0).setCellValue(cle);
        ligne.createCell(1).setCellValue(valeur);
        return indice + 1;
    }

    private static void repartition(Sheet feuille, String premiereColonne, Map<String, Long> donnees) {
        var entete = feuille.createRow(0);
        entete.createCell(0).setCellValue(premiereColonne);
        entete.createCell(1).setCellValue("Count");
        int indice = 1;
        for (Map.Entry<String, Long> entree : donnees.entrySet()) {
            var ligne = feuille.createRow(indice++);
            ligne.createCell(0).setCellValue(entree.getKey());
            ligne.createCell(1).setCellValue(entree.getValue().doubleValue());
        }
    }

    private static String tableauHtml(String titre, String premiereColonne, Map<String, Long> donnees) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(titre).append("</h2>");
        sb.append("<table><thead><tr><th>").append(premiereColonne).append("</th><th>Count</th></tr></thead><tbody>");
        if (donnees.isEmpty()) {
            sb.append("<tr><td colspan='2'>Aucune donnée</td></tr>");
        } else {
            donnees.forEach((k, v) -> sb.append("<tr><td>").append(k).append("</td><td>").append(v).append("</td></tr>"));
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }
}
