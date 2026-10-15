package com.hemodialyse.backend.contexts.comptabilite.domain.ecriture;

import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;

import java.util.Collection;
import java.util.Locale;

/**
 * Numéro d'une écriture dans son journal et son année : {@code <JOURNAL>-<année>-<n° sur 6 chiffres>}, par exemple
 * {@code VE-2026-000042} (RG-CPT-010). Le suivant est le plus grand NOMBRE existant plus un : passé 999999 le numéro compte
 * sept chiffres sans retomber à 000001, et un suffixe non numérique est ignoré. Un numéro lu en base garde sa valeur telle
 * quelle (numéros hors motif hérités).
 */
public record NumeroDePiece(String valeur) {

    /**
     * Plus long suffixe pris en compte : au-delà, le suffixe est ignoré comme un suffixe non numérique.
     */
    static final int CHIFFRES_MAX = 15;

    public NumeroDePiece {
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException("Le numéro de pièce est obligatoire");
        }
    }

    public static NumeroDePiece de(String valeur) {
        return new NumeroDePiece(valeur);
    }

    /**
     * Le numéro qui suit les numéros {@code existants} du journal, dans l'année : sans les autres journaux ni années.
     */
    public static NumeroDePiece suivant(CodeJournal journal, int annee, Collection<String> existants) {
        String prefixe = prefixe(journal, annee);
        long plusGrand = 0;
        for (String existant : existants) {
            if (existant != null && existant.startsWith(prefixe)) {
                plusGrand = Math.max(plusGrand, suffixeNumerique(existant.substring(prefixe.length())));
            }
        }
        return new NumeroDePiece(prefixe + String.format(Locale.ROOT, "%06d", plusGrand + 1));
    }

    /**
     * {@code VE-2026-} : ce que partagent tous les numéros du journal pour l'année.
     */
    public static String prefixe(CodeJournal journal, int annee) {
        return journal.valeur() + "-" + annee + "-";
    }

    /**
     * La valeur du suffixe s'il ne comporte que des chiffres (1 à 15) ; zéro sinon.
     */
    private static long suffixeNumerique(String suffixe) {
        if (suffixe.isEmpty() || suffixe.length() > CHIFFRES_MAX) {
            return 0;
        }
        for (int i = 0; i < suffixe.length(); i++) {
            char c = suffixe.charAt(i);
            if (c < '0' || c > '9') {
                return 0;
            }
        }
        return Long.parseLong(suffixe);
    }

    @Override
    public String toString() {
        return valeur;
    }
}
