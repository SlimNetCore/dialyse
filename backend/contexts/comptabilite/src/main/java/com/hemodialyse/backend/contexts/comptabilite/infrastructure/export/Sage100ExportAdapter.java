package com.hemodialyse.backend.contexts.comptabilite.infrastructure.export;

import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.ExportComptablePort;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.LigneEcriture;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Adaptateur sortant CSV Sage 100 (format historique conservé).
 */
public final class Sage100ExportAdapter implements ExportComptablePort {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final String SEPARATEUR = ";";
    private static final String CRLF = "\r\n";

    private static String montant(BigDecimal montant) {
        return montant == null || montant.signum() == 0 ? "" : montant.toPlainString().replace('.', ',');
    }

    private static String echapper(String valeur) {
        if (valeur == null) return "";
        if (valeur.contains(SEPARATEUR) || valeur.contains("\"") || valeur.contains("\n")) {
            return "\"" + valeur.replace("\"", "\"\"") + "\"";
        }
        return valeur;
    }

    @Override
    public byte[] exporter(List<EcritureComptable> ecritures) {
        StringBuilder csv = new StringBuilder("Journal;Piece;Date;Compte;Libelle;Debit;Credit;Lettrage\r\n");
        for (EcritureComptable ecriture : ecritures) {
            for (LigneEcriture ligne : ecriture.lignes()) {
                String lettrage = ligne.tiers() == null ? "" : ligne.tiers().toString().substring(0, 8).toUpperCase();
                csv.append(ecriture.journal().valeur()).append(SEPARATEUR)
                        .append(ecriture.numeroDePiece().valeur()).append(SEPARATEUR)
                        .append(ecriture.datePiece().format(DATE)).append(SEPARATEUR)
                        .append(ligne.compte()).append(SEPARATEUR)
                        .append(echapper(ligne.libelle())).append(SEPARATEUR)
                        .append(montant(ligne.debit())).append(SEPARATEUR)
                        .append(montant(ligne.credit())).append(SEPARATEUR)
                        .append(lettrage).append(CRLF);
            }
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
}
