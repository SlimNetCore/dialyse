package com.hemodialyse.backend.contexts.facturation.application.reglement.pagination;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lecture stricte des filtres de colonne des listes paginées de la Facturation (contrat {@code pagination-serveur}, mêmes
 * règles que le Stock) :
 * <ul>
 *   <li>texte : « contient », insensible à la casse, espaces de bord retirés ; vide ou blanc = pas de filtre ;</li>
 *   <li>numérique : {@code [op]nombre} avec {@code op} parmi {@code = != > >= < <=} (défaut {@code =}), plage
 *   {@code a..b} (bornes incluses), {@code a..}, {@code ..b} ; virgule ou point décimal ; illisible ou plage inversée
 *   = 400 {@code FILTRE_INVALIDE} ;</li>
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

    public enum Comparateur { EGAL, DIFFERENT, SUPERIEUR, SUPERIEUR_OU_EGAL, INFERIEUR, INFERIEUR_OU_EGAL, ENTRE }

    /**
     * Filtre numérique lu : {@code valeur} est l'opérande (borne basse pour {@link Comparateur#ENTRE}) ;
     * {@code borneHaute} n'est renseignée que pour {@link Comparateur#ENTRE} (bornes incluses).
     */
    public record FiltreNumerique(Comparateur comparateur, BigDecimal valeur, BigDecimal borneHaute) {

        public FiltreNumerique(Comparateur comparateur, BigDecimal valeur) {
            this(comparateur, valeur, null);
        }

        public boolean accepte(BigDecimal nombre) {
            if (nombre == null) return false;
            int c = nombre.compareTo(valeur);
            return switch (comparateur) {
                case EGAL -> c == 0;
                case DIFFERENT -> c != 0;
                case SUPERIEUR -> c > 0;
                case SUPERIEUR_OU_EGAL -> c >= 0;
                case INFERIEUR -> c < 0;
                case INFERIEUR_OU_EGAL -> c <= 0;
                case ENTRE -> c >= 0 && nombre.compareTo(borneHaute) <= 0;
            };
        }
    }

    private static final Pattern NOMBRE = Pattern.compile("[+-]?(?:\\d+(?:[.,]\\d*)?|[.,]\\d+)");

    /**
     * Grammaire (espaces ignorés) : {@code [op]n} avec {@code op} parmi {@code = != > >= < <=} (défaut {@code =}),
     * {@code a..b} (entre, bornes incluses, {@code a <= b}), {@code a..} ({@code >= a}), {@code ..b} ({@code <= b}) ;
     * nombres décimaux avec point ou virgule.
     *
     * @throws PaginationInvalide {@code FILTRE_INVALIDE} pour toute autre saisie (ou {@code a > b})
     */
    public static Optional<FiltreNumerique> numerique(String nom, String valeur) {
        if (valeur == null || valeur.isBlank()) return Optional.empty();
        String v = valeur.replaceAll("\\s+", "");
        if (v.contains("...")) throw illisible(nom, valeur);
        int plage = v.indexOf("..");
        if (plage >= 0) {
            String basse = v.substring(0, plage);
            String haute = v.substring(plage + 2);
            if (basse.isEmpty() && haute.isEmpty()) throw illisible(nom, valeur);
            if (haute.isEmpty()) return Optional.of(new FiltreNumerique(Comparateur.SUPERIEUR_OU_EGAL, nombre(nom, valeur, basse)));
            if (basse.isEmpty()) return Optional.of(new FiltreNumerique(Comparateur.INFERIEUR_OU_EGAL, nombre(nom, valeur, haute)));
            BigDecimal a = nombre(nom, valeur, basse);
            BigDecimal b = nombre(nom, valeur, haute);
            if (a.compareTo(b) > 0) {
                throw new PaginationInvalide(PaginationInvalide.FILTRE_INVALIDE,
                        "Plage inversee pour le filtre « " + nom + " » : " + valeur);
            }
            return Optional.of(new FiltreNumerique(Comparateur.ENTRE, a, b));
        }
        Comparateur comparateur = Comparateur.EGAL;
        if (v.startsWith("!=")) { comparateur = Comparateur.DIFFERENT; v = v.substring(2); }
        else if (v.startsWith(">=")) { comparateur = Comparateur.SUPERIEUR_OU_EGAL; v = v.substring(2); }
        else if (v.startsWith("<=")) { comparateur = Comparateur.INFERIEUR_OU_EGAL; v = v.substring(2); }
        else if (v.startsWith(">")) { comparateur = Comparateur.SUPERIEUR; v = v.substring(1); }
        else if (v.startsWith("<")) { comparateur = Comparateur.INFERIEUR; v = v.substring(1); }
        else if (v.startsWith("=")) { v = v.substring(1); }
        return Optional.of(new FiltreNumerique(comparateur, nombre(nom, valeur, v)));
    }

    private static BigDecimal nombre(String nom, String saisie, String texte) {
        if (!NOMBRE.matcher(texte).matches()) throw illisible(nom, saisie);
        String n = texte.replace(',', '.');
        if (n.endsWith(".")) n = n.substring(0, n.length() - 1);
        return new BigDecimal(n);
    }

    private static PaginationInvalide illisible(String nom, String saisie) {
        return new PaginationInvalide(PaginationInvalide.FILTRE_INVALIDE, "Filtre numerique illisible « " + nom + " » : " + saisie);
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
