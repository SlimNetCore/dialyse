package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.reglement.LigneDeSituation;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.TypeDeSolde;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Formats de l'export des règlements, reproduits à l'identique (KB-FAC-48) : CSV séparé par {@code ;}, sans échappement, sans
 * BOM, sans total, sauf l'apostrophe devant une cellule de texte qui ressemble à une formule (KB-FAC-R02) ; classeur Excel d'une feuille « Règlements ». Le solde est signé (trop-perçu en négatif). Le mode de
 * règlement n'est pas exporté : l'export est une ligne par facture et ses colonnes restent celles d'avant F4.
 */
final class ExportDesSituations {

    private static final String[] ENTETES = {
            "N° Facture", "N° Assurance", "Nom", "Prénom", "Caisse", "Agence",
            "Centre payeur", "Date facturation", "Montant facturé", "Montant réglé",
            "Reste / trop-perçu", "Solde type", "État"
    };
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ExportDesSituations() {
    }

    static byte[] csv(List<LigneDeSituation> lignes) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(";", ENTETES)).append('\n');
        for (LigneDeSituation ligne : lignes) {
            sb.append(sansFormule(ligne.numeroFacture())).append(';')
                    .append(sansFormule(ligne.numeroAssurance())).append(';')
                    .append(sansFormule(ligne.patientNom())).append(';')
                    .append(sansFormule(ligne.patientPrenom())).append(';')
                    .append(sansFormule(ligne.caisse())).append(';')
                    .append(sansFormule(ligne.agence())).append(';')
                    .append(sansFormule(ligne.centrePayeur())).append(';')
                    .append(ligne.dateFacturation() != null ? ligne.dateFacturation().format(JOUR) : "").append(';')
                    .append(ligne.montantFacture()).append(';')
                    .append(ligne.montantRegle()).append(';')
                    .append(ligne.soldeType() == TypeDeSolde.TROP_PERCU ? ligne.tropPercu().negate() : ligne.reste()).append(';')
                    .append(ligne.soldeType()).append(';')
                    .append(ligne.etat()).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Neutralise l'injection de formule (KB-FAC-R02, OWASP) : une cellule de texte qui commence par {@code = + - @}, une
     * tabulation ou un retour chariot est préfixée d'une apostrophe. Les montants (colonnes numériques) ne passent pas ici.
     */
    private static String sansFormule(String texte) {
        if (texte == null || texte.isEmpty()) return texte;
        return "=+-@\t\r".indexOf(texte.charAt(0)) >= 0 ? "'" + texte : texte;
    }

    static byte[] excel(List<LigneDeSituation> lignes) throws IOException {
        try (Workbook classeur = new XSSFWorkbook()) {
            Sheet feuille = classeur.createSheet("Règlements");
            Row entete = feuille.createRow(0);
            for (int i = 0; i < ENTETES.length; i++) {
                entete.createCell(i).setCellValue(ENTETES[i]);
            }
            int rang = 1;
            for (LigneDeSituation ligne : lignes) {
                Row row = feuille.createRow(rang++);
                texte(row, 0, ligne.numeroFacture());
                texte(row, 1, ligne.numeroAssurance());
                texte(row, 2, ligne.patientNom());
                texte(row, 3, ligne.patientPrenom());
                texte(row, 4, ligne.caisse());
                texte(row, 5, ligne.agence());
                texte(row, 6, ligne.centrePayeur());
                texte(row, 7, ligne.dateFacturation() != null ? ligne.dateFacturation().format(JOUR) : "");
                row.createCell(8).setCellValue(ligne.montantFacture() != null ? ligne.montantFacture().doubleValue() : 0d);
                row.createCell(9).setCellValue(ligne.montantRegle() != null ? ligne.montantRegle().doubleValue() : 0d);
                double solde = ligne.soldeType() == TypeDeSolde.TROP_PERCU
                        ? (ligne.tropPercu() != null ? ligne.tropPercu().negate().doubleValue() : 0d)
                        : (ligne.reste() != null ? ligne.reste().doubleValue() : 0d);
                row.createCell(10).setCellValue(solde);
                texte(row, 11, ligne.soldeType() != null ? ligne.soldeType().name() : "");
                texte(row, 12, ligne.etat() != null ? ligne.etat().name() : "");
            }
            for (int i = 0; i < ENTETES.length; i++) {
                feuille.autoSizeColumn(i);
            }
            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            classeur.write(sortie);
            return sortie.toByteArray();
        }
    }

    private static void texte(Row row, int colonne, String valeur) {
        Cell cellule = row.createCell(colonne);
        cellule.setCellValue(valeur != null ? valeur : "");
    }
}
