package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Critères de la recherche paginée des attestations du centre (mêmes filtres que l'existant). Page positive ou nulle, taille de
 * 1 à 200 (400 sinon). {@code debut} et {@code fin} portent sur les dates de l'attestation : date exacte ou intervalle
 * {@code a..b} (un intervalle inversé est redressé), formats {@code yyyy-MM-dd} ou {@code dd/MM/yyyy}, avec ou sans heure.
 * <p>
 * Décision du 2026-11-04 (KB-PAT-45) : une date illisible est un 400 (avant : le filtre était ignoré en silence et tout était
 * rendu) ; les jokers {@code %} et {@code _} des filtres texte sont des caractères ordinaires (échappés par l'adapter).
 */
public record CriteresDAttestation(int page, int size, String search, String code, String nom, String assurance,
                                   Intervalle debut, Intervalle fin) {

    /** Bornes incluses ; une borne nulle est ouverte. */
    public record Intervalle(LocalDate de, LocalDate a) {
    }

    public CriteresDAttestation {
        if (page < 0) throw new SaisieInvalide("La page ne peut pas être négative");
        if (size < 1 || size > 200) throw new SaisieInvalide("La taille doit être comprise entre 1 et 200");
    }

    /** Critères depuis les saisies brutes (dates en texte). */
    public static CriteresDAttestation de(int page, int size, String search, String code, String nom, String assurance,
                                          String debut, String fin) {
        return new CriteresDAttestation(page, size, search, code, nom, assurance, lire("debut", debut), lire("fin", fin));
    }

    static Intervalle lire(String champ, String valeur) {
        String brut = valeur == null ? "" : valeur.trim();
        if (brut.isBlank()) return null;
        if (!brut.contains("..")) {
            LocalDate exacte = date(champ, brut);
            return new Intervalle(exacte, exacte);
        }
        String[] parties = brut.split("\\.\\.", 2);
        String deBrut = parties[0].trim();
        String aBrut = parties.length > 1 ? parties[1].trim() : "";
        LocalDate de = deBrut.isBlank() ? null : date(champ, deBrut);
        LocalDate a = aBrut.isBlank() ? null : date(champ, aBrut);
        if (de != null && a != null && de.isAfter(a)) return new Intervalle(a, de);
        return new Intervalle(de, a);
    }

    private static LocalDate date(String champ, String valeur) {
        String brut = valeur.trim();
        if (brut.contains("T")) brut = brut.substring(0, brut.indexOf('T'));
        if (brut.contains(" ")) brut = brut.substring(0, brut.indexOf(' '));
        try {
            return LocalDate.parse(brut);
        } catch (Exception ignoree) {
            // essai du format jour/mois/année
        }
        try {
            return LocalDate.parse(brut, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception illisible) {
            throw new SaisieInvalide("Date illisible dans le filtre « " + champ + " » : " + valeur);
        }
    }
}
