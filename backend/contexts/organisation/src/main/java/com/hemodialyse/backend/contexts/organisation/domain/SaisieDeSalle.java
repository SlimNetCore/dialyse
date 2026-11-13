package com.hemodialyse.backend.contexts.organisation.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * La saisie d'une salle, contrôlée (PL11-4, ADR 0160) : code (obligatoire, 50), nom (obligatoire, 255), salle d'isolement (NON ou OUI,
 * absent = non précisé) et capacité en générateurs (entier de 1 à 999, absent = non précisée). Impossible à construire invalide
 * ({@link #controler}) ; mêmes anomalies, codes et messages que l'administration des référentiels (PL11-0).
 * <p>
 * D8 : un champ non précisé ne dit rien ; c'est à la salle de décider de le conserver (modification) ou de prendre sa valeur par défaut
 * (création).
 */
public record SaisieDeSalle(String code, String nom, Boolean isolement, Integer capacite) {

    public static final int LONGUEUR_MAX_DU_CODE = 50;
    public static final int LONGUEUR_MAX_DU_NOM = 255;
    public static final int CAPACITE_MAX = 999;

    private static final String LIBELLE_ISOLEMENT = "Salle d'isolement";
    private static final String LIBELLE_CAPACITE = "Capacité (générateurs max.)";

    public static SaisieDeSalle controler(String code, String nom, String isolement, String capacite) {
        List<AnomalieDeSaisie> anomalies = new ArrayList<>();
        String c = texte("code", "Code", code, LONGUEUR_MAX_DU_CODE, anomalies);
        String n = texte("nom", "Nom", nom, LONGUEUR_MAX_DU_NOM, anomalies);
        Boolean i = isolement(nettoyer(isolement), anomalies);
        Integer k = capacite(nettoyer(capacite), anomalies);
        if (!anomalies.isEmpty()) throw new SaisieDeSalleInvalide(anomalies);
        return new SaisieDeSalle(c, n, i, k);
    }

    private static String nettoyer(String brut) {
        if (brut == null) return null;
        String valeur = brut.replace(' ', ' ').trim();
        return valeur.isEmpty() ? null : valeur;
    }

    private static String texte(String cle, String libelleDuChamp, String brut, int max, List<AnomalieDeSaisie> anomalies) {
        String valeur = nettoyer(brut);
        if (valeur == null) {
            anomalies.add(new AnomalieDeSaisie(cle, "REQUIRED", "« " + libelleDuChamp + " » est obligatoire.", Map.of()));
            return null;
        }
        if (valeur.length() > max) {
            anomalies.add(new AnomalieDeSaisie(cle, "TOO_LONG",
                    "« " + libelleDuChamp + " » dépasse " + max + " caractères (" + valeur.length() + ").",
                    Map.of("max", String.valueOf(max), "length", String.valueOf(valeur.length()))));
            return null;
        }
        return valeur;
    }

    /** « oui » ou « Oui » valent OUI ; « non » vaut NON ; toute autre valeur est refusée. */
    private static Boolean isolement(String valeur, List<AnomalieDeSaisie> anomalies) {
        if (valeur == null) return null;
        String code = Normalizer.normalize(valeur, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .trim().toUpperCase(Locale.ROOT).replaceAll("[\\s\\-]+", "_");
        if (code.equals("OUI")) return Boolean.TRUE;
        if (code.equals("NON")) return Boolean.FALSE;
        anomalies.add(new AnomalieDeSaisie("isolement", "INVALID_VALUE",
                "« " + LIBELLE_ISOLEMENT + " » : « " + valeur + " » n'est pas une valeur autorisée (NON, OUI).",
                Map.of("value", valeur, "allowed", "NON, OUI")));
        return null;
    }

    private static Integer capacite(String valeur, List<AnomalieDeSaisie> anomalies) {
        if (valeur == null) return null;
        String compact = valeur.replaceAll("[\\s\\u00A0\\u202F]", "");
        int nombre;
        try {
            nombre = Integer.parseInt(compact);
        } catch (NumberFormatException e) {
            anomalies.add(new AnomalieDeSaisie("capacite", "INVALID_INTEGER",
                    "« " + LIBELLE_CAPACITE + " » : « " + valeur + " » n'est pas un nombre entier (ex. 12).", Map.of("value", valeur)));
            return null;
        }
        if (nombre < 1 || nombre > CAPACITE_MAX) {
            anomalies.add(new AnomalieDeSaisie("capacite", "INTEGER_OUT_OF_RANGE",
                    "« " + LIBELLE_CAPACITE + " » doit être compris entre 1 et " + CAPACITE_MAX + ".",
                    Map.of("value", valeur, "max", String.valueOf(CAPACITE_MAX))));
            return null;
        }
        return nombre;
    }

    /** Clé d'unicité d'un code : casse et espaces ignorés. */
    public String cle() {
        return SaisieDeCreneau.cleDe(code);
    }
}
