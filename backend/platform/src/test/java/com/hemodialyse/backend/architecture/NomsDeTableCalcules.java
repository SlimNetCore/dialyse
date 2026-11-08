package com.hemodialyse.backend.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Détection des noms de table CALCULÉS ou INDIRECTS (KB-SEA-92, SE7e, ADR 0099), technique partagée par les gardes de tables : une requête
 * construite ({@code "SELECT COUNT(*) FROM " + usage.table()}, {@code new StringBuilder("FROM ").append(table.name())}) n'a pas de
 * littéral {@code FROM seances} ; le nom de la table est une chaîne {@code "seances"} posée ailleurs (descripteur, constante, liste).
 * <p>
 * Règle : un source de production qui contient le LITTÉRAL exact d'une table surveillée ({@code "seances"} ou {@code "public.seances"},
 * entre guillemets, casse ignorée) est un lecteur indirect s'il construit lui-même une requête, OU si un source qui construit une requête
 * le nomme (nom simple de sa classe). La constante est ainsi rattachée au SQL qui la consomme, même dans un autre fichier
 * ({@code ReferentialAdminTables} lu par {@code ReferentialAdminJdbcAdapter}). Une chaîne qui n'atteint jamais une requête (code d'URL,
 * clé JSON) dans un fichier nommé par un constructeur de SQL est un FAUX POSITIF assumé : la garde l'inscrit nommément, avec sa raison.
 */
final class NomsDeTableCalcules {

    /** Fin d'un littéral SQL suivie d'une concaténation ou d'un {@code append} : le nom de la table vient d'une variable. */
    private static final Pattern SQL_CONSTRUIT = Pattern.compile(
            "\\b(?:FROM|JOIN|INTO|UPDATE|TABLE)\\s*\"\\s*(?:\\+|\\)\\s*\\.\\s*append\\s*\\()", Pattern.CASE_INSENSITIVE);

    private NomsDeTableCalcules() {
    }

    static boolean construitUneRequete(String texte) {
        return SQL_CONSTRUIT.matcher(texte).find();
    }

    static boolean contientLeLitteral(String texte, Set<String> tables) {
        for (String table : tables) {
            Pattern litteral = Pattern.compile("\"(?:public\\.)?" + Pattern.quote(table) + "\"", Pattern.CASE_INSENSITIVE);
            if (litteral.matcher(texte).find()) return true;
        }
        return false;
    }

    private static String nomSimple(Path source) {
        String nom = source.getFileName().toString();
        return nom.endsWith(".java") ? nom.substring(0, nom.length() - ".java".length()) : nom;
    }

    /**
     * Les sources Java de {@code sources} qui portent un littéral d'une des {@code tables} rattaché à une requête construite (dans le
     * fichier, ou dans un fichier qui construit une requête et nomme sa classe).
     */
    static Set<Path> lecteursIndirects(List<Path> sources, Set<String> tables) {
        Map<Path, String> textes = new HashMap<>();
        for (Path source : sources) {
            if (!source.toString().endsWith(".java")) continue;
            try {
                textes.put(source, Files.readString(source));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        List<String> constructeurs = textes.entrySet().stream().filter(e -> construitUneRequete(e.getValue())).map(Map.Entry::getValue).toList();
        Set<Path> indirects = new TreeSet<>();
        textes.forEach((source, texte) -> {
            if (!contientLeLitteral(texte, tables)) return;
            if (construitUneRequete(texte)) {
                indirects.add(source);
                return;
            }
            Pattern nomme = Pattern.compile("\\b" + Pattern.quote(nomSimple(source)) + "\\b");
            if (constructeurs.stream().anyMatch(c -> nomme.matcher(c).find())) indirects.add(source);
        });
        return indirects;
    }
}
