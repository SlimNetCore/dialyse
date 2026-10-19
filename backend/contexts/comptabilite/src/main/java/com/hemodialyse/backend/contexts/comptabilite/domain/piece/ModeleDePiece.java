package com.hemodialyse.backend.contexts.comptabilite.domain.piece;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Agrégat décrivant les lignes réutilisables d’une pièce comptable saisie.
 */
public record ModeleDePiece(UUID id, CentreId centre, String code, String libelle, CodeJournal journal, boolean actif,
                            List<Ligne> lignes) {

    public static final int CODE_MAX = 20;
    public static final int LIBELLE_MAX = 100;
    public static final int LIGNES_MAX = 20;
    private static final Pattern CODE = Pattern.compile("[A-Z0-9_-]{1," + CODE_MAX + "}");

    public ModeleDePiece {
        if (id == null || centre == null)
            throw new IllegalArgumentException("L'identifiant et le centre sont obligatoires");
        if (journal == null) throw new IllegalArgumentException("Le journal du modèle est obligatoire");
        code = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Le code du modèle comporte 1 à " + CODE_MAX
                    + " lettres majuscules, chiffres, tirets ou soulignés");
        }
        libelle = texte(libelle, "Le libellé du modèle", LIBELLE_MAX);
        lignes = lignes == null ? List.of() : List.copyOf(lignes);
        if (lignes.size() < 2 || lignes.size() > LIGNES_MAX) {
            throw new ReglePiece("MODELE_LIGNES_INVALIDES", "Un modèle de pièce comporte de 2 à " + LIGNES_MAX + " lignes");
        }
        if (lignes.stream().noneMatch(l -> l.sens() == Sens.DEBIT)
                || lignes.stream().noneMatch(l -> l.sens() == Sens.CREDIT)) {
            throw new ReglePiece("MODELE_LIGNES_INVALIDES",
                    "Un modèle de pièce comporte au moins une ligne au débit et une ligne au crédit");
        }
    }

    private static String texte(String valeur, String nom, int max) {
        if (valeur == null || valeur.isBlank()) throw new IllegalArgumentException(nom + " est obligatoire");
        String net = valeur.trim();
        if (net.length() > max) throw new IllegalArgumentException(nom + " dépasse " + max + " caractères");
        return net;
    }

    public enum Sens {DEBIT, CREDIT}

    public record Ligne(Sens sens, String compte, String libelle) {
        public Ligne {
            if (sens == null) throw new IllegalArgumentException("Le sens de la ligne est obligatoire");
            if (compte == null || compte.isBlank())
                throw new IllegalArgumentException("Le compte de la ligne est obligatoire");
            compte = compte.trim();
            libelle = libelle == null || libelle.isBlank() ? null : libelle.trim();
            if (libelle != null && libelle.length() > LIBELLE_MAX) {
                throw new IllegalArgumentException("Le libellé de la ligne dépasse " + LIBELLE_MAX + " caractères");
            }
        }
    }

    public static final class ReglePiece extends RegleMetierViolee {
        public ReglePiece(String code, String message) {
            super(code, message);
        }
    }
}
