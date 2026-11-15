package com.hemodialyse.backend.contexts.stock.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.stock.domain.inventaire.LigneDInventaire;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Les lignes du procès-verbal d'inventaire (documents.v1, DOC5j, ADR 0202) : le jeu de données que le Stock fournit au gabarit
 * {@code inventaire_stock}, qui n'embarque plus de requête. Un lot par ligne, dans l'ordre de l'ancienne requête (libellé de l'article sans
 * casse, identifiant de l'article, péremption absente d'abord, position de la ligne) ; mêmes noms de champs (majuscules) et mêmes types que
 * ceux du gabarit. L'état de péremption se lit au jour d'édition (expiré avant ce jour, proche dans les 90 jours qui suivent).
 */
public final class LignesDuProcesVerbal {

    static final Map<String, String> LIBELLES_DES_MOTIFS = Map.of(
            "CASSE", "Casse", "PEREMPTION", "Péremption", "ERREUR_SAISIE", "Erreur de saisie", "PERTE", "Perte / vol",
            "NON_ENREGISTRE", "Mouvement non enregistré", "RETOUR", "Retour", "AUTRE", "Autre");
    private static final String A_JUSTIFIER = "À justifier";
    private static final int JOURS_DE_PEREMPTION_PROCHE = 90;

    private LignesDuProcesVerbal() {
    }

    public static List<Map<String, Object>> de(List<LigneDInventaire> lignes, LocalDate jourDEdition) {
        List<LigneDInventaire> ordonnees = new ArrayList<>(lignes);
        ordonnees.sort(Comparator
                .comparing((LigneDInventaire l) -> l.articleLibelle() == null ? "" : l.articleLibelle().toLowerCase(Locale.ROOT))
                .thenComparing(LigneDInventaire::articleId, LignesDuProcesVerbal::comparerSansSigne)
                .thenComparing(LigneDInventaire::datePeremption, Comparator.nullsFirst(Comparator.naturalOrder())));
        List<Map<String, Object>> cartes = new ArrayList<>();
        for (LigneDInventaire l : ordonnees) cartes.add(carte(l, jourDEdition));
        return cartes;
    }

    /** L'ordre des identifiants de la base : octets en non signé (le tri naturel de {@link UUID} est signé). */
    private static int comparerSansSigne(UUID a, UUID b) {
        int haut = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return haut != 0 ? haut : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }

    private static Map<String, Object> carte(LigneDInventaire l, LocalDate jour) {
        Map<String, Object> c = new HashMap<>();
        c.put("ARTICLE_KEY", l.articleId().toString());
        c.put("ARTICLE_CODE", l.articleCode());
        c.put("ARTICLE_LIBELLE", l.articleLibelle());
        c.put("UNITE", l.unite());
        c.put("LOT_LIBELLE", (l.numeroLot() == null ? "sans lot" : l.numeroLot()) + (l.ajoutee() ? " (trouvé)" : ""));
        c.put("DATE_PEREMPTION", l.datePeremption() == null ? null : Date.valueOf(l.datePeremption()));
        c.put("PEREMPTION_ETAT", etatDePeremption(l.datePeremption(), jour));
        c.put("QUANTITE_THEORIQUE", l.quantiteTheorique());
        c.put("QUANTITE_COMPTEE", l.quantiteComptee());
        c.put("NON_COMPTE", l.estComptee() ? 0 : 1);
        c.put("ECART", l.ecart());
        c.put("PMP", l.pmp());
        c.put("VAL_ECART", l.valeurEcart());
        c.put("COMPTE_PAR", l.comptePar());
        c.put("MOTIF", motif(l));
        return c;
    }

    private static String etatDePeremption(LocalDate peremption, LocalDate jour) {
        if (peremption == null) return null;
        if (peremption.isBefore(jour)) return "EXPIRE";
        if (!peremption.isAfter(jour.plusDays(JOURS_DE_PEREMPTION_PROCHE))) return "PROCHE";
        return null;
    }

    private static String motif(LigneDInventaire l) {
        if (l.motifEcart() != null) return LIBELLES_DES_MOTIFS.getOrDefault(l.motifEcart(), l.motifEcart());
        return l.aUnEcart() ? A_JUSTIFIER : null;
    }
}
