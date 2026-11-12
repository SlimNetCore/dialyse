package com.hemodialyse.backend.contexts.documents.domain;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Les noms de paramètres de gabarit que le SERVEUR fournit toujours : la valeur envoyée par un client est ignorée (KB-DOC1-2, ADR 0142).
 * Inventaire (insensible à la casse) :
 * <ul>
 *   <li>identité : préfixes {@code SOCIETE_} et {@code CENTRE_} (posés par le papier en-tête) ;</li>
 *   <li>centre : {@code CENTER_ID} (le centre de l'impression, gardé par la session) ;</li>
 *   <li>totaux de la synthèse de facturation : {@code TOTAL_HT_FACTURES}, {@code TOTAL_TVA_FACTURES}, {@code TOTAL_TTC_FACTURES} ;</li>
 *   <li>moteur : préfixes {@code REPORT_} et {@code JASPER_}, et {@code IS_IGNORE_PAGINATION} (paramètres internes de Jasper).</li>
 * </ul>
 * Les appelants de confiance (autres contextes, qui passent par le port de rendu) gardent CENTER_ID et les paramètres du moteur : seule
 * l'identité leur est retirée ({@link #ecarterLIdentite}).
 */
public final class ParametresReserves {

    private static final String PREFIXE_SOCIETE = "SOCIETE_";
    private static final String PREFIXE_CENTRE = "CENTRE_";

    private ParametresReserves() {
    }

    /** Un nom d'identité (SOCIETE_*, CENTRE_*), quelle que soit la casse. */
    public static boolean estUneIdentite(String nom) {
        String cle = majuscules(nom);
        return cle.startsWith(PREFIXE_SOCIETE) || cle.startsWith(PREFIXE_CENTRE);
    }

    /** Un nom que le serveur fournit toujours aux requêtes HTTP. */
    public static boolean estReserve(String nom) {
        String cle = majuscules(nom);
        return estUneIdentite(nom) || cle.equals("CENTER_ID") || cle.equals("TOTAL_HT_FACTURES") || cle.equals("TOTAL_TVA_FACTURES")
                || cle.equals("TOTAL_TTC_FACTURES") || cle.startsWith("REPORT_") || cle.startsWith("JASPER_")
                || cle.equals("IS_IGNORE_PAGINATION");
    }

    /** Retire les noms d'identité. */
    public static <V> Map<String, V> ecarterLIdentite(Map<String, V> parametres) {
        return filtrer(parametres, false);
    }

    /** Retire tous les noms réservés. */
    public static <V> Map<String, V> ecarterLesReserves(Map<String, V> parametres) {
        return filtrer(parametres, true);
    }

    private static <V> Map<String, V> filtrer(Map<String, V> parametres, boolean toutesLesReserves) {
        Map<String, V> sures = new HashMap<>();
        parametres.forEach((cle, valeur) -> {
            boolean ecarte = toutesLesReserves ? estReserve(cle) : estUneIdentite(cle);
            if (!ecarte) sures.put(cle, valeur);
        });
        return sures;
    }

    private static String majuscules(String nom) {
        return nom == null ? "" : nom.toUpperCase(Locale.ROOT);
    }
}
