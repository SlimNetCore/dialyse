package com.hemodialyse.backend.domain.comptabilite.aggregate;

import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.SensEcriture;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Modèle de pièce comptable défini par un centre (loyer, salaires, facture fournisseur, opération diverse…) : le
 * journal où elle s'écrit et ses lignes — un sens et un compte chacune. Saisir une pièce de ce modèle ne demande plus
 * que la date, le libellé et les montants. Créer un nouveau type de pièce ne demande donc aucun développement.
 * <p>
 * Invariants : 2 à {@value #LIGNES_MAX} lignes, au moins une au débit et une au crédit (sans quoi aucune pièce de ce
 * modèle ne pourrait être équilibrée).
 *
 * @param actif un modèle désactivé ne sert plus à saisir de nouvelles pièces ; les pièces déjà saisies demeurent
 */
public record ModelePiece(UUID id, UUID centerId, String code, String libelle, JournalCode journal, boolean actif,
                          List<Ligne> lignes) {

    public static final int CODE_MAX = 20;
    public static final int LIBELLE_MAX = 100;
    public static final int LIGNES_MAX = 20;
    private static final Pattern CODE = Pattern.compile("[A-Z0-9_-]{1," + CODE_MAX + "}");

    public ModelePiece {
        if (id == null || centerId == null)
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
            throw new BusinessException("MODELE_LIGNES_INVALIDES",
                    "Un modèle de pièce comporte de 2 à " + LIGNES_MAX + " lignes");
        }
        if (lignes.stream().noneMatch(l -> l.sens() == SensEcriture.DEBIT)
                || lignes.stream().noneMatch(l -> l.sens() == SensEcriture.CREDIT)) {
            throw new BusinessException("MODELE_LIGNES_INVALIDES",
                    "Un modèle de pièce comporte au moins une ligne au débit et une ligne au crédit");
        }
    }

    private static String texte(String valeur, String nom, int max) {
        if (valeur == null || valeur.isBlank()) throw new IllegalArgumentException(nom + " est obligatoire");
        String net = valeur.trim();
        if (net.length() > max) throw new IllegalArgumentException(nom + " dépasse " + max + " caractères");
        return net;
    }

    /**
     * Ligne d'un modèle : le sens et le compte sont fixés, le montant se saisit à chaque pièce.
     *
     * @param libelle libellé proposé pour la ligne ; vide = celui de la pièce
     */
    public record Ligne(SensEcriture sens, String compte, String libelle) {
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
}
