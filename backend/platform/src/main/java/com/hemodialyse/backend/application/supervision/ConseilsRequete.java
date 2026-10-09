package com.hemodialyse.backend.application.supervision;

import com.hemodialyse.backend.application.supervision.Conseil.NiveauConseil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Règles qui transforment les statistiques d'une requête en pistes d'amélioration. Classe pure : elle ne regarde que
 * le texte normalisé et les compteurs, jamais la base. Ce sont des <b>indices</b>, pas des diagnostics : le bouton
 * « Analyser » confirme (ou non) avec le plan d'exécution.
 */
public final class ConseilsRequete {

    static final double LIGNES_PAR_APPEL_ELEVE = 1000;
    static final long APPELS_TRES_NOMBREUX = 10_000;
    static final double APPEL_RAPIDE_MS = 10;
    static final double PART_APPELS_PCT = 10;
    static final double LENT_MS = 50;
    static final double LENT_FONCTION_MS = 20;
    static final double PEU_DE_LIGNES = 50;
    static final long MIN_BLOCS_CACHE = 1000;
    static final double CACHE_FAIBLE_PCT = 90;
    static final long MIN_APPELS_VARIABILITE = 20;
    static final double FACTEUR_VARIABILITE = 20;
    static final double MAX_VARIABILITE_MS = 200;

    private static final Pattern SELECT_ETOILE = Pattern.compile("select\\s+(distinct\\s+)?([a-z_][a-z0-9_]*\\.)?\\*");
    private static final Pattern FONCTION_SUR_COLONNE = Pattern.compile(
            "\\b(lower|upper|date|date_trunc|to_char|cast|coalesce)\\s*\\(\\s*[a-z_][a-z0-9_.]*\\s*(,\\s*'[^']*'\\s*)?\\)\\s*(=|<|>|like|in\\b)");
    private static final Pattern IN_LONG = Pattern.compile("\\bin\\s*\\((\\s*\\$\\d+\\s*,){9,}");
    private static final Pattern ESPACES = Pattern.compile("\\s+");

    private ConseilsRequete() {
    }

    /**
     * @param partTotalPct part (en %) du temps cumulé de toute la base
     * @return les pistes, celles à traiter d'abord
     */
    public static List<Conseil> evaluer(RequeteStatistique r, double partTotalPct) {
        String sql = ESPACES.matcher(r.requete().toLowerCase(Locale.ROOT).trim()).replaceAll(" ");
        boolean lecture = sql.startsWith("select") || sql.startsWith("with");
        boolean ecriture = sql.startsWith("insert") || sql.startsWith("update") || sql.startsWith("delete");
        double moyenne = r.tempsMoyenMs();
        double lignesParAppel = r.lignesParAppel();
        boolean filtre = sql.contains(" where ");
        List<Conseil> conseils = new ArrayList<>();

        if (lecture && lignesParAppel >= LIGNES_PAR_APPEL_ELEVE && !sql.contains(" limit ")) {
            conseils.add(action("LIGNES_PAR_APPEL_ELEVE", Math.round(lignesParAppel)));
        }
        if (lecture && filtre && moyenne >= LENT_MS && lignesParAppel < PEU_DE_LIGNES) {
            conseils.add(action("SCAN_PROBABLE", Math.round(moyenne)));
        }
        if (lecture && sql.contains("count(") && moyenne >= LENT_MS) {
            conseils.add(action("COMPTAGE_EXHAUSTIF", Math.round(moyenne)));
        }
        if (lecture && r.blocsTemporaires() > 0) {
            conseils.add(action("TRI_SUR_DISQUE", r.blocsTemporaires()));
        }
        if (r.blocsCache() + r.blocsDisque() >= MIN_BLOCS_CACHE && r.tauxCachePct() < CACHE_FAIBLE_PCT) {
            conseils.add(action("CACHE_FAIBLE", Math.round(r.tauxCachePct())));
        }
        if (ecriture && moyenne >= LENT_MS) {
            conseils.add(action("ECRITURE_LENTE", Math.round(moyenne)));
        }
        if (r.appels() >= APPELS_TRES_NOMBREUX && moyenne < APPEL_RAPIDE_MS && partTotalPct >= PART_APPELS_PCT) {
            conseils.add(info("APPELS_TRES_NOMBREUX", r.appels()));
        }
        if (lecture && moyenne >= LENT_FONCTION_MS && SELECT_ETOILE.matcher(sql).find()) {
            conseils.add(info("SELECT_ETOILE", null));
        }
        if (lecture && moyenne >= LENT_MS && (sql.contains(" ilike ") || sql.contains(" like "))) {
            conseils.add(info("RECHERCHE_TEXTE", null));
        }
        if (lecture && moyenne >= LENT_FONCTION_MS && FONCTION_SUR_COLONNE.matcher(sql).find()) {
            conseils.add(info("FONCTION_SUR_COLONNE", null));
        }
        if (lecture && IN_LONG.matcher(sql).find()) {
            conseils.add(info("IN_LONG", null));
        }
        if (r.appels() >= MIN_APPELS_VARIABILITE && r.tempsMaxMs() >= MAX_VARIABILITE_MS
                && r.tempsMaxMs() >= FACTEUR_VARIABILITE * Math.max(moyenne, 1)) {
            conseils.add(info("VARIABILITE", Math.round(r.tempsMaxMs())));
        }
        conseils.sort(Comparator.comparing(Conseil::niveau));
        return conseils;
    }

    private static Conseil action(String code, long valeur) {
        return new Conseil(code, NiveauConseil.ACTION, String.valueOf(valeur));
    }

    private static Conseil info(String code, Long valeur) {
        return new Conseil(code, NiveauConseil.INFO, valeur == null ? "" : String.valueOf(valeur));
    }
}
