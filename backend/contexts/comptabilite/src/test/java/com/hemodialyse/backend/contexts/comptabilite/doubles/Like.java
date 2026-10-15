package com.hemodialyse.backend.contexts.comptabilite.doubles;

import java.util.regex.Pattern;

/**
 * Le {@code LIKE} de SQL pour les doubles en mémoire : {@code %} = toute suite de caractères, {@code _} = un caractère.
 */
final class Like {

    private Like() {
    }

    static boolean correspond(String motif, String texte) {
        StringBuilder regex = new StringBuilder();
        for (char c : motif.toCharArray()) {
            switch (c) {
                case '%' -> regex.append(".*");
                case '_' -> regex.append('.');
                default -> regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString(), Pattern.DOTALL).matcher(texte).matches();
    }
}
