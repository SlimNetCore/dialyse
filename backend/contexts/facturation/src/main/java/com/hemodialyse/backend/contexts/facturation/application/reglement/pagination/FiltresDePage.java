package com.hemodialyse.backend.contexts.facturation.application.reglement.pagination;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Lecture stricte des filtres de colonne des listes paginées de la Facturation (contrat {@code pagination-serveur}, mêmes
 * règles que le Stock) :
 * <ul>
 *   <li>texte : « contient », insensible à la casse, espaces de bord retirés ; vide ou blanc = pas de filtre ;</li>
 *   <li>numérique : {@code [op]nombre} avec {@code op} parmi {@code = > >= < <=} (défaut {@code =}), virgule ou point
 *   décimal ; illisible = 400 {@code FILTRE_INVALIDE} ;</li>
 *   <li>énumération : une ou plusieurs valeurs séparées par des virgules (OU), sensibles à la casse ; inconnue = 400
 *   {@code FILTRE_INVALIDE}.</li>
 * </ul>
 */
public final class FiltresDePage {

    private FiltresDePage() {
    }

    /** Texte normalisé (minuscules, sans espaces de bord) ; vide si absent ou blanc. */
    public static Optional<String> texte(String valeur) {
        if (valeur == null || valeur.isBlank()) return Optional.empty();
        return Optional.of(valeur.trim().toLowerCase(Locale.ROOT));
    }

    public enum Comparateur { EGAL, SUPERIEUR, SUPERIEUR_OU_EGAL, INFERIEUR, INFERIEUR_OU_EGAL }

    public record FiltreNumerique(Comparateur comparateur, BigDecimal valeur) {

        public boolean accepte(BigDecimal nombre) {
            if (nombre == null) return false;
            int c = nombre.compareTo(valeur);
            return switch (comparateur) {
                case EGAL -> c == 0;
                case SUPERIEUR -> c > 0;
                case SUPERIEUR_OU_EGAL -> c >= 0;
                case INFERIEUR -> c < 0;
                case INFERIEUR_OU_EGAL -> c <= 0;
            };
        }
    }

    /** @throws PaginationInvalide {@code FILTRE_INVALIDE} si la valeur n'est pas {@code [op]nombre} */
    public static Optional<FiltreNumerique> numerique(String nom, String valeur) {
        if (valeur == null || valeur.isBlank()) return Optional.empty();
        String v = valeur.trim().replace(" ", "");
        Comparateur comparateur = Comparateur.EGAL;
        if (v.startsWith(">=")) { comparateur = Comparateur.SUPERIEUR_OU_EGAL; v = v.substring(2); }
        else if (v.startsWith("<=")) { comparateur = Comparateur.INFERIEUR_OU_EGAL; v = v.substring(2); }
        else if (v.startsWith(">")) { comparateur = Comparateur.SUPERIEUR; v = v.substring(1); }
        else if (v.startsWith("<")) { comparateur = Comparateur.INFERIEUR; v = v.substring(1); }
        else if (v.startsWith("=")) { v = v.substring(1); }
        try {
            return Optional.of(new FiltreNumerique(comparateur, new BigDecimal(v.replace(',', '.'))));
        } catch (NumberFormatException e) {
            throw new PaginationInvalide(PaginationInvalide.FILTRE_INVALIDE, "Filtre numerique illisible « " + nom + " » : " + valeur);
        }
    }

    /** @throws PaginationInvalide {@code FILTRE_INVALIDE} si une valeur n'appartient pas à l'énumération */
    public static <E extends Enum<E>> Set<E> enumeration(String nom, String valeur, Class<E> type) {
        if (valeur == null || valeur.isBlank()) return EnumSet.noneOf(type);
        EnumSet<E> valeurs = EnumSet.noneOf(type);
        for (String brute : valeur.split(",")) {
            String v = brute.trim();
            if (v.isEmpty()) continue;
            valeurs.add(Arrays.stream(type.getEnumConstants()).filter(e -> e.name().equals(v)).findFirst()
                    .orElseThrow(() -> new PaginationInvalide(PaginationInvalide.FILTRE_INVALIDE,
                            "Valeur inconnue pour le filtre « " + nom + " » : " + v)));
        }
        return valeurs;
    }
}
