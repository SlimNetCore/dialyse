package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Les données de la synthèse mensuelle telles que le modèle de document {@code SYNTHESE_FACTURATION_MENSUELLE} les attend (PL8, ADR 0147) :
 * paramètres (période, indicateurs, totaux enregistrés, répartitions en barres de texte) et lignes du tableau forfait x caisse sous la clé
 * {@value #LIGNES} (le moteur les sert au gabarit comme source de données ; le gabarit ne contient aucune requête, ADR 0140 D3). Chaque
 * ligne porte {@code FORFAIT}, {@code COLONNE} (rang de la colonne sur 4 chiffres puis libellé de la caisse : le rang ordonne les colonnes,
 * le libellé s'affiche) , {@code HT} et {@code TTC}. Partagé par l'impression de la Facturation et par la voie « modèles de documents ».
 */
public final class ModeleDeLaSynthese {

    public static final String LIGNES = "LIGNES";
    /** Largeur du rang qui préfixe le libellé d'une colonne. */
    public static final int LARGEUR_DU_RANG = 4;

    private ModeleDeLaSynthese() {
    }

    public static Map<String, Object> parametres(SyntheseMensuelle synthese) {
        Map<String, Object> p = new HashMap<>();
        p.put("PERIOD_START", synthese.debut().toString());
        p.put("PERIOD_END", synthese.fin().toString());
        p.put("DISTRIBUTION_BY_CAISSE_TEXT", BarresDeRepartition.texte(synthese.parCaisse()));
        p.put("DISTRIBUTION_BY_STATUS_TEXT", BarresDeRepartition.texte(synthese.parSituation()));
        p.put("TOTAL_PATIENTS", synthese.patients());
        p.put("TOTAL_SEANCES", synthese.seances());
        p.put("TOTAL_FACTURES", synthese.factures());
        p.put("TOTAL_HT_FACTURES", synthese.totalHt());
        p.put("TOTAL_TVA_FACTURES", synthese.totalTva());
        p.put("TOTAL_TTC_FACTURES", synthese.totalTtc());
        p.put(LIGNES, lignes(synthese));
        return p;
    }

    /**
     * Voie « modèles de documents » ({@code /documents/print}) : le classement historique des caisses en quatre colonnes fixes, CNAS,
     * CASNOS, CAMSSP et Autres (préfixe du code de caisse, KB-RF4-02), et le graphique du TTC par forfait ({@code AVEC_GRAPHIQUE}).
     * Les quatre colonnes sont toujours présentes (une ligne à zéro les porte au besoin, sur le premier forfait).
     */
    public static Map<String, Object> parametresClasses(SyntheseMensuelle synthese) {
        Map<String, Object> p = parametres(synthese);
        p.put(LIGNES, lignesClassees(synthese));
        p.put("AVEC_GRAPHIQUE", Boolean.TRUE);
        return p;
    }

    private static final List<String> CLASSES = List.of("CNAS", "CASNOS", "CAMSSP", "Autres");

    private static int classe(String code) {
        String majuscules = code == null ? "" : code.toUpperCase(java.util.Locale.ROOT);
        for (int i = 0; i < 3; i++) {
            if (majuscules.startsWith(CLASSES.get(i))) {
                return i;
            }
        }
        return 3;
    }

    static List<Map<String, Object>> lignesClassees(SyntheseMensuelle synthese) {
        Map<List<Object>, BigDecimal[]> cellules = new java.util.TreeMap<>((a, b) -> {
            int c = ((String) a.get(0)).compareTo((String) b.get(0));
            return c != 0 ? c : Integer.compare((Integer) a.get(1), (Integer) b.get(1));
        });
        for (SyntheseMensuelle.Ligne ligne : synthese.lignes()) {
            BigDecimal[] somme = cellules.computeIfAbsent(List.of(ligne.forfait(), classe(ligne.caisse())),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            somme[0] = somme[0].add(ligne.ht());
            somme[1] = somme[1].add(ligne.ttc());
        }
        if (!cellules.isEmpty()) {
            String premier = (String) cellules.keySet().iterator().next().get(0);
            for (int i = 0; i < CLASSES.size(); i++) {
                final int rang = i;
                boolean presente = cellules.keySet().stream().anyMatch(k -> (Integer) k.get(1) == rang);
                if (!presente) {
                    cellules.put(List.of(premier, rang), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                }
            }
        }
        List<Map<String, Object>> lignes = new ArrayList<>();
        cellules.forEach((cle, somme) -> {
            Map<String, Object> ligne = new HashMap<>();
            ligne.put("FORFAIT", cle.get(0));
            ligne.put("COLONNE", String.format("%0" + LARGEUR_DU_RANG + "d", cle.get(1)) + CLASSES.get((Integer) cle.get(1)));
            ligne.put("HT", somme[0]);
            ligne.put("TTC", somme[1]);
            lignes.add(ligne);
        });
        return lignes;
    }

    static List<Map<String, Object>> lignes(SyntheseMensuelle synthese) {
        List<SyntheseMensuelle.Caisse> colonnes = synthese.colonnes();
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (SyntheseMensuelle.Ligne ligne : synthese.lignes()) {
            int rang = 0;
            String libelle = ligne.caisse();
            for (int i = 0; i < colonnes.size(); i++) {
                if (colonnes.get(i).code().equals(ligne.caisse())) {
                    rang = i;
                    libelle = colonnes.get(i).libelle();
                    break;
                }
            }
            Map<String, Object> ligneDuModele = new HashMap<>();
            ligneDuModele.put("FORFAIT", ligne.forfait());
            ligneDuModele.put("COLONNE", String.format("%0" + LARGEUR_DU_RANG + "d", rang) + libelle);
            ligneDuModele.put("HT", ligne.ht());
            ligneDuModele.put("TTC", ligne.ttc());
            lignes.add(ligneDuModele);
        }
        return lignes;
    }
}
