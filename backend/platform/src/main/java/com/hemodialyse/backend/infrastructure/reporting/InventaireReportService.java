package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.domain.stock.model.Inventaire;
import com.hemodialyse.backend.domain.stock.model.InventaireStatut;
import com.hemodialyse.backend.domain.stock.model.LigneInventaire;
import net.sf.jasperreports.engine.JRParameter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Procès-verbal d'inventaire : modèle de document {@value ModeleDocumentCatalog#INVENTAIRE_STOCK}, imprimé via
 * {@link ModeleDocumentPrinter} (version personnalisée du centre si elle existe, identité société + centre posée par
 * le serveur). Les lignes sont lues par la requête du modèle ; l'en-tête de l'inventaire, les indicateurs et
 * l'analyse des écarts par motif sont calculés ici et passés en paramètres.
 */
@Service
public class InventaireReportService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");
    private static final Map<String, String> MOTIFS = Map.of(
            "CASSE", "Casse", "PEREMPTION", "Péremption", "ERREUR_SAISIE", "Erreur de saisie", "PERTE", "Perte / vol",
            "NON_ENREGISTRE", "Mouvement non enregistré", "RETOUR", "Retour", "AUTRE", "Autre");

    private final ModeleDocumentPrinter printer;

    public InventaireReportService(ModeleDocumentPrinter printer) {
        this.printer = printer;
    }

    private static void indicateurs(Map<String, Object> p, List<LigneInventaire> lignes) {
        long articles = lignes.stream().map(LigneInventaire::getArticleId).distinct().count();
        long comptees = lignes.stream().filter(LigneInventaire::isComptee).count();
        long ecarts = lignes.stream().filter(LigneInventaire::hasEcart).count();
        BigDecimal theo = sum(lignes.stream().map(l -> l.getQuantiteTheorique().multiply(l.getPmp())).toList());
        BigDecimal compte = sum(lignes.stream().filter(LigneInventaire::isComptee)
                .map(l -> l.getQuantiteComptee().multiply(l.getPmp())).toList());
        BigDecimal ecart = sum(lignes.stream().map(LigneInventaire::valeurEcart).toList());
        int total = lignes.size();

        p.put("KPI_ARTICLES_LOTS", articles + " / " + total);
        p.put("KPI_COMPTES", comptees + " / " + total);
        p.put("KPI_AVANCEMENT", percent(comptees, total) + " % d'avancement");
        p.put("KPI_ECARTS", String.valueOf(ecarts));
        p.put("KPI_ECARTS_PART", percent(ecarts, total) + " % des lots");
        p.put("KPI_VAL_THEO", money(theo));
        p.put("KPI_VAL_COMPTE", money(compte));
        p.put("KPI_VAL_ECART", signedMoney(ecart));
        p.put("KPI_VAL_ECART_PART", theo.signum() == 0 ? "DA"
                : "DA · " + signed(ecart.multiply(BigDecimal.valueOf(100)).divide(theo, 2, RoundingMode.HALF_UP), "0.00") + " %");
        p.put("INV_ECART_SIGNE", ecart.signum());
    }

    /**
     * Écarts par motif (du plus important au plus faible), en colonnes alignées ligne à ligne.
     */
    private static void motifs(Map<String, Object> p, List<LigneInventaire> lignes) {
        Map<String, BigDecimal[]> parMotif = new LinkedHashMap<>();
        for (LigneInventaire l : lignes) {
            if (!l.hasEcart()) continue;
            String motif = l.getMotifEcart() == null ? "Non justifié" : MOTIFS.getOrDefault(l.getMotifEcart(), l.getMotifEcart());
            BigDecimal[] acc = parMotif.computeIfAbsent(motif, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal v = l.valeurEcart();
            acc[0] = acc[0].add(BigDecimal.ONE);
            if (v.signum() < 0) acc[1] = acc[1].add(v);
            else acc[2] = acc[2].add(v);
        }
        List<Map.Entry<String, BigDecimal[]>> rows = new ArrayList<>(parMotif.entrySet());
        rows.sort(Comparator.comparing((Map.Entry<String, BigDecimal[]> e) -> e.getValue()[1].add(e.getValue()[2]).abs())
                .reversed().thenComparing(Map.Entry::getKey));

        List<String> libelles = new ArrayList<>();
        List<String> lots = new ArrayList<>();
        List<String> manquants = new ArrayList<>();
        List<String> excedents = new ArrayList<>();
        List<String> nets = new ArrayList<>();
        BigDecimal totLots = BigDecimal.ZERO;
        BigDecimal totMoins = BigDecimal.ZERO;
        BigDecimal totPlus = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal[]> e : rows) {
            BigDecimal[] v = e.getValue();
            libelles.add(e.getKey());
            lots.add(v[0].toPlainString());
            manquants.add(v[1].signum() == 0 ? "—" : money(v[1]) + " DA");
            excedents.add(v[2].signum() == 0 ? "—" : "+" + money(v[2]) + " DA");
            nets.add(signedMoney(v[1].add(v[2])) + " DA");
            totLots = totLots.add(v[0]);
            totMoins = totMoins.add(v[1]);
            totPlus = totPlus.add(v[2]);
        }
        p.put("MOTIFS_PRESENTS", !rows.isEmpty());
        p.put("MOTIFS_LIBELLES", String.join("\n", libelles));
        p.put("MOTIFS_LOTS", String.join("\n", lots));
        p.put("MOTIFS_MANQUANTS", String.join("\n", manquants));
        p.put("MOTIFS_EXCEDENTS", String.join("\n", excedents));
        p.put("MOTIFS_NETS", String.join("\n", nets));
        p.put("MOTIFS_TOTAL_LOTS", totLots.toPlainString());
        p.put("MOTIFS_TOTAL_MANQUANTS", money(totMoins) + " DA");
        p.put("MOTIFS_TOTAL_EXCEDENTS", "+" + money(totPlus) + " DA");
        p.put("MOTIFS_TOTAL_NET", signedMoney(totMoins.add(totPlus)) + " DA");
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static long percent(long part, long total) {
        return total == 0 ? 0 : Math.round(part * 100.0 / total);
    }

    private static String money(BigDecimal v) {
        return format(v.setScale(2, RoundingMode.HALF_UP), "#,##0.00");
    }

    private static String signedMoney(BigDecimal v) {
        return signed(v.setScale(2, RoundingMode.HALF_UP), "#,##0.00");
    }

    private static String signed(BigDecimal v, String pattern) {
        return (v.signum() > 0 ? "+" : "") + format(v, pattern);
    }

    /**
     * Séparateur de milliers : espace insécable classique (présente dans les polices PDF standard).
     */
    private static String format(BigDecimal v, String pattern) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        symbols.setGroupingSeparator('\u00A0');
        symbols.setDecimalSeparator(',');
        symbols.setMinusSign('-');
        return new DecimalFormat(pattern, symbols).format(v);
    }

    private static String stamp(OffsetDateTime t) {
        return t == null ? "—" : t.atZoneSameInstant(ZoneId.systemDefault()).format(STAMP);
    }

    private static String by(String user) {
        return user == null || user.isBlank() ? "" : " par " + user;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public ModeleDocumentPrinter.Document imprimer(Inventaire inv, String editePar) {
        return printer.print(inv.getCenterId(), ModeleDocumentCatalog.INVENTAIRE_STOCK,
                params(inv, editePar, OffsetDateTime.now()));
    }

    Map<String, Object> params(Inventaire inv, String editePar, OffsetDateTime editeLe) {
        Map<String, Object> p = new HashMap<>();
        p.put(JRParameter.REPORT_LOCALE, Locale.FRANCE);
        InventaireStatut statut = inv.getStatut();
        p.put("INVENTAIRE_ID", inv.getId().toString());
        p.put("INV_REFERENCE", inv.getReference());
        p.put("INV_DATE", inv.getDateInventaire().format(DAY));
        p.put("INV_STATUT", statut.name());
        p.put("INV_STATUT_LIBELLE", switch (statut) {
            case EN_COURS -> "EN COURS";
            case CLOTURE -> "CLÔTURÉ";
            case ANNULE -> "ANNULÉ";
        });
        p.put("INV_COMMENTAIRE", blankToNull(inv.getCommentaire()));
        p.put("INV_OUVERT", stamp(inv.getCreatedAt()) + by(inv.getCreatedBy()));
        p.put("INV_CLOTURE_LIBELLE", switch (statut) {
            case EN_COURS -> null;
            case CLOTURE -> "Clôture";
            case ANNULE -> "Annulation";
        });
        p.put("INV_CLOTURE", statut == InventaireStatut.EN_COURS ? null : stamp(inv.getClosedAt()) + by(inv.getClosedBy()));
        p.put("INV_NOTICE", switch (statut) {
            case EN_COURS ->
                    "Document provisoire : l'inventaire est en cours, les quantités et les écarts peuvent encore "
                            + "évoluer jusqu'à la clôture. Les mouvements de stock du centre sont suspendus.";
            case ANNULE -> "Inventaire annulé : ce comptage n'a eu aucun effet sur le stock.";
            case CLOTURE -> null;
        });
        p.put("INV_FILIGRANE", switch (statut) {
            case EN_COURS -> "PROVISOIRE";
            case ANNULE -> "ANNULÉ";
            case CLOTURE -> null;
        });
        p.put("SIGNATAIRE_ETABLI", blankToNull(inv.getCreatedBy()));
        p.put("SIGNATAIRE_VERIFIE", statut == InventaireStatut.CLOTURE ? blankToNull(inv.getClosedBy()) : null);
        p.put("EDITE_LE", stamp(editeLe));
        p.put("EDITE_PAR", blankToNull(editePar));
        indicateurs(p, inv.getLignes());
        motifs(p, inv.getLignes());
        return p;
    }
}

