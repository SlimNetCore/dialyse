package com.hemodialyse.backend.contexts.gmao.domain.piecejointe;

import java.text.Normalizer;
import java.util.Optional;

/**
 * Nom d'une pièce jointe, toujours assaini (R22, KB-GMAO-60 corrigé le 2026-10-12). {@link #assainir} :
 * <ul>
 *   <li>normalise en NFC ;</li>
 *   <li>retire les caractères de contrôle (dont U+0085), de formatage (dont l'inversion de sens U+202E et les marques
 *       bidirectionnelles), les séparateurs de ligne et de paragraphe, les moitiés de paire de substitution isolées, et
 *       {@code " < > : * ? |} ;</li>
 *   <li>ne garde que ce qui suit le dernier séparateur de chemin ({@code /} ou {@code \}) ;</li>
 *   <li>réduit toute suite de points à un seul (plus de {@code ..}) et retire les blancs (y compris l'espace insécable et les autres espaces Unicode) et les points de tête et de queue ;</li>
 *   <li>remplace un nom vide par {@code document} ;</li>
 *   <li>garde au plus les 150 derniers caractères (l'extension reste visible), sans couper une paire de substitution (emoji).</li>
 * </ul>
 * L'opération est idempotente : on peut la rejouer à la lecture (KB-GMAO-61) sans changer un nom déjà assaini.
 */
public record NomDeFichier(String valeur) {

    public static final int LONGUEUR_MAX = 150;
    private static final String PAR_DEFAUT = "document";
    private static final String INTERDITS = "\"<>:*?|";

    public static NomDeFichier assainir(String brut) {
        String normalise = brut == null ? "" : Normalizer.normalize(brut, Normalizer.Form.NFC);
        StringBuilder gardes = new StringBuilder();
        normalise.codePoints().forEach(cp -> {
            if (cp == '\\') {
                gardes.append('/');
            } else if (!aRetirer(cp)) {
                gardes.appendCodePoint(cp);
            }
        });
        String nom = gardes.substring(gardes.lastIndexOf("/") + 1);
        while (nom.contains("..")) nom = nom.replace("..", ".");
        nom = rogner(nom);
        if (nom.length() > LONGUEUR_MAX) {
            int debut = nom.length() - LONGUEUR_MAX;
            if (Character.isLowSurrogate(nom.charAt(debut))) debut++;
            nom = rogner(nom.substring(debut));
        }
        return new NomDeFichier(nom.isEmpty() ? PAR_DEFAUT : nom);
    }

    private static boolean aRetirer(int cp) {
        if (cp < 0x10000 && INTERDITS.indexOf(cp) >= 0) return true;
        return switch (Character.getType(cp)) {
            case Character.CONTROL, Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR,
                 Character.SURROGATE -> true;
            default -> false;
        };
    }

    private static String rogner(String nom) {
        int debut = 0;
        int fin = nom.length();
        while (debut < fin && estBlancOuPoint(nom.charAt(debut))) debut++;
        while (fin > debut && estBlancOuPoint(nom.charAt(fin - 1))) fin--;
        return nom.substring(debut, fin);
    }

    private static boolean estBlancOuPoint(char c) {
        return c == '.' || Character.isWhitespace(c) || Character.isSpaceChar(c);
    }

    /**
     * Extension du nom, en minuscules, si le nom en porte une : lettres et chiffres, de 1 à 8 caractères, après le dernier point.
     * « Rapport 12.5 final » n'a pas d'extension ; « plan.EXE » a l'extension {@code exe}.
     */
    public Optional<String> extension() {
        int point = valeur.lastIndexOf('.');
        if (point < 0) return Optional.empty();
        String suite = valeur.substring(point + 1);
        if (suite.isEmpty() || suite.length() > 8 || !suite.chars().allMatch(c -> c < 128 && Character.isLetterOrDigit(c))) {
            return Optional.empty();
        }
        return Optional.of(suite.toLowerCase(java.util.Locale.ROOT));
    }
}
