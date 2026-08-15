package com.hemodialyse.backend.infrastructure.export;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.entity.LigneEcriture;
import com.hemodialyse.backend.domain.comptabilite.port.ExportComptablePort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Adaptateur d'export Sage 100 — format CSV à colonnes fixes.
 * <p>
 * Colonnes : JournalCode ; NumeroPiece ; DatePiece (ddMMyyyy) ; CompteSCF ; Libelle ; Debit ; Credit ; CodeLettrage
 * <p>
 * NOTE : ce format est indicatif (Sage 100 Compta version standard algérienne).
 * La structure exacte (séparateur, format date, colonnes analytiques) doit être
 * confirmée avec l'expert-comptable avant la première importation en production.
 * Modifier uniquement cet adaptateur sans toucher au domaine.
 */
@Component
public class Sage100ExportAdapter implements ExportComptablePort {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final String SEP = ";";
    private static final String CRLF = "\r\n";

    @Override
    public byte[] exporter(List<EcritureComptable> ecritures) {
        StringBuilder sb = new StringBuilder();
        // En-tête
        sb.append("Journal").append(SEP)
                .append("Piece").append(SEP)
                .append("Date").append(SEP)
                .append("Compte").append(SEP)
                .append("Libelle").append(SEP)
                .append("Debit").append(SEP)
                .append("Credit").append(SEP)
                .append("Lettrage").append(CRLF);

        for (EcritureComptable e : ecritures) {
            for (LigneEcriture l : e.getLignes()) {
                sb.append(e.getJournalCode().name()).append(SEP)
                        .append(e.getNumeroPiece()).append(SEP)
                        .append(e.getDatePiece().format(DATE_FMT)).append(SEP)
                        .append(l.getCompteSCF()).append(SEP)
                        .append(escapeCsv(l.getLibelleLigne())).append(SEP)
                        .append(formatAmount(l.getMontantDebit())).append(SEP)
                        .append(formatAmount(l.getMontantCredit())).append(SEP)
                        .append(l.getTiersId() != null ? l.getTiersId().toString().substring(0, 8).toUpperCase() : "")
                        .append(CRLF);
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String formatId() {
        return "SAGE100";
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) return "";
        return amount.toPlainString().replace('.', ',');
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(SEP) || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}

