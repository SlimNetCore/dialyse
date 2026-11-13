package com.hemodialyse.backend.contexts.organisation.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Le code et le libellé d'un créneau, contrôlés : nettoyés (espaces, espace insécable), obligatoires, bornés (code 50, libellé 255
 * caractères). Impossible à construire invalide ({@link #controler}) ; mêmes anomalies, codes et messages que l'administration des
 * référentiels (PL11-0).
 */
public record SaisieDeCreneau(String code, String libelle) {

    public static final int LONGUEUR_MAX_DU_CODE = 50;
    public static final int LONGUEUR_MAX_DU_LIBELLE = 255;

    public static SaisieDeCreneau controler(String code, String libelle) {
        List<AnomalieDeSaisie> anomalies = new ArrayList<>();
        String c = champ("code", "Code", code, LONGUEUR_MAX_DU_CODE, anomalies);
        String l = champ("libelle", "Libellé", libelle, LONGUEUR_MAX_DU_LIBELLE, anomalies);
        if (!anomalies.isEmpty()) throw new SaisieDeCreneauInvalide(anomalies);
        return new SaisieDeCreneau(c, l);
    }

    private static String champ(String cle, String libelleDuChamp, String brut, int max, List<AnomalieDeSaisie> anomalies) {
        String valeur = brut == null ? null : brut.replace(' ', ' ').trim();
        if (valeur == null || valeur.isEmpty()) {
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

    /** Clé d'unicité d'un code : casse et espaces ignorés. */
    public static String cleDe(String code) {
        return code == null ? "" : code.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public String cle() {
        return cleDe(code);
    }
}
