package com.hemodialyse.backend.application.supervision;

import com.hemodialyse.backend.application.supervision.AnalyseRequete.BalayageComplet;
import com.hemodialyse.backend.application.supervision.AnalyseRequete.Verdict;
import com.hemodialyse.backend.application.supervision.port.AnalysePlanPort.PlanGenerique;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lit un plan d'exécution PostgreSQL au format JSON et en tire l'essentiel : quelles tables sont lues en entier, sur
 * quelles colonnes elles sont filtrées, et si un index serait utile. Classe pure : les index et tailles de tables
 * existants sont fournis par l'appelant. Les suggestions d'index sont des <b>pistes à valider</b>.
 */
public final class AnalyseurPlan {

    /**
     * En dessous, lire toute la table est plus rapide que de passer par un index.
     */
    static final long SEUIL_TABLE_PETITE = 1000;
    static final int COLONNES_INDEX_MAX = 3;
    private static final int NOM_INDEX_MAX = 63;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /**
     * {@code [alias.]colonne [)][::type] opérateur} dans le texte d'un filtre.
     */
    private static final Pattern CONDITION = Pattern.compile(
            "(?:[a-z_][a-z0-9_]*\\.)?([a-z_][a-z0-9_]*)\\)?(?:::[a-z_ ]+?)?\\)?\\s*(=|<>|>=|<=|>|<)\\s");
    private static final Pattern APPEL_DE_FONCTION = Pattern.compile("([a-z_][a-z0-9_]*)\\s*\\(+\\s*$");
    private static final Pattern IDENTIFIANT = Pattern.compile("[a-z_][a-z0-9_]*");
    private static final Pattern COLONNES_INDEX = Pattern.compile("\\(([^()]*)\\)\\s*(where .*)?$");
    private static final Set<String> MOTS_CLES = Set.of("and", "or", "not", "where");

    private AnalyseurPlan() {
    }

    public static AnalyseRequete analyser(PlanGenerique plan, Function<String, List<String>> indexDe,
                                          ToLongFunction<String> lignesDe) {
        if (plan.raisonIndisponible() != null) {
            return AnalyseRequete.indisponible(plan.requete(), plan.raisonIndisponible());
        }
        JsonNode racine;
        try {
            racine = JSON.readTree(plan.json());
        } catch (RuntimeException e) {
            return AnalyseRequete.indisponible(plan.requete(), "PLAN_IMPOSSIBLE");
        }
        JsonNode noeud = racine.path(0).path("Plan");
        if (noeud.isMissingNode()) {
            return AnalyseRequete.indisponible(plan.requete(), "PLAN_IMPOSSIBLE");
        }
        List<BalayageComplet> balayages = new ArrayList<>();
        int[] index = {0};
        parcourir(noeud, balayages, index, indexDe, lignesDe);
        return new AnalyseRequete(true, null, plan.requete(), plan.texte(), noeud.path("Total Cost").asDouble(0),
                balayages, index[0]);
    }

    private static void parcourir(JsonNode noeud, List<BalayageComplet> balayages, int[] index,
                                  Function<String, List<String>> indexDe, ToLongFunction<String> lignesDe) {
        String type = noeud.path("Node Type").asString("");
        if (type.contains("Index Scan") || type.contains("Index Only Scan") || type.contains("Bitmap Index Scan")) {
            index[0]++;
        }
        if (type.contains("Seq Scan") && !noeud.path("Relation Name").isMissingNode()) {
            balayages.add(balayage(noeud.path("Relation Name").asString(""), noeud.path("Filter").asString(""),
                    indexDe, lignesDe));
        }
        for (JsonNode enfant : noeud.path("Plans")) {
            parcourir(enfant, balayages, index, indexDe, lignesDe);
        }
    }

    private static BalayageComplet balayage(String table, String filtre, Function<String, List<String>> indexDe,
                                            ToLongFunction<String> lignesDe) {
        List<String> colonnes = colonnesFiltrees(filtre);
        long lignes = lignesDe.applyAsLong(table);
        List<String> existants = indexDe.apply(table);
        Verdict verdict;
        String suggestion = null;
        if (colonnes.isEmpty()) {
            verdict = Verdict.SANS_FILTRE;
        } else if (lignes < SEUIL_TABLE_PETITE) {
            verdict = Verdict.TABLE_PETITE;
        } else {
            List<String> retenues = colonnes.subList(0, Math.min(COLONNES_INDEX_MAX, colonnes.size()));
            if (existants.stream().anyMatch(def -> couvre(def, retenues.get(0)))) {
                verdict = Verdict.INDEX_PRESENT;
            } else {
                verdict = Verdict.INDEX_RECOMMANDE;
                suggestion = ordreCreation(table, retenues);
            }
        }
        return new BalayageComplet(table, filtre, colonnes, lignes, existants, verdict, suggestion);
    }

    /**
     * Colonnes d'un filtre, égalités d'abord (ordre d'un index efficace : égalités puis première colonne de plage).
     * Les colonnes enveloppées dans une fonction ({@code lower(nom) = …}) sont ignorées : un index simple ne sert pas.
     */
    static List<String> colonnesFiltrees(String filtre) {
        if (filtre == null || filtre.isBlank()) {
            return List.of();
        }
        String sql = filtre.toLowerCase(Locale.ROOT);
        Set<String> egalites = new LinkedHashSet<>();
        Set<String> plages = new LinkedHashSet<>();
        Matcher m = CONDITION.matcher(sql);
        while (m.find()) {
            String avant = sql.substring(Math.max(0, m.start() - 40), m.start());
            Matcher fonction = APPEL_DE_FONCTION.matcher(avant);
            if (fonction.find() && !MOTS_CLES.contains(fonction.group(1))) {
                continue;
            }
            (m.group(2).equals("=") ? egalites : plages).add(m.group(1));
        }
        plages.removeAll(egalites);
        List<String> colonnes = new ArrayList<>(egalites);
        plages.stream().findFirst().ifPresent(colonnes::add);
        return colonnes;
    }

    /**
     * Vrai si la définition d'index commence par la colonne donnée.
     */
    static boolean couvre(String definition, String colonne) {
        Matcher m = COLONNES_INDEX.matcher(definition.toLowerCase(Locale.ROOT).trim());
        if (!m.find()) {
            return false;
        }
        String premiere = m.group(1).split(",")[0].trim().split("\\s+")[0];
        return premiere.equals(colonne);
    }

    static String ordreCreation(String table, List<String> colonnes) {
        if (!IDENTIFIANT.matcher(table).matches() || !colonnes.stream().allMatch(c -> IDENTIFIANT.matcher(c).matches())) {
            return null;
        }
        String nom = "idx_" + table + "_" + String.join("_", colonnes);
        if (nom.length() > NOM_INDEX_MAX) {
            nom = nom.substring(0, NOM_INDEX_MAX);
        }
        return "CREATE INDEX IF NOT EXISTS " + nom + " ON " + table + " (" + String.join(", ", colonnes) + ");";
    }
}
