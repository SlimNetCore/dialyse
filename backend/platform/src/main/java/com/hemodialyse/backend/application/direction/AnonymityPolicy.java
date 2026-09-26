package com.hemodialyse.backend.application.direction;

/**
 * Anonymat des tableaux de bord de direction : seuls des agrégats sont exposés, et tout effectif faible — qui
 * permettrait de reconnaître des patients dans un petit centre ou un petit groupe — est masqué.
 * <p>
 * Un effectif compris entre 1 et {@link #THRESHOLD} - 1 est remplacé par {@code null} (affiché « &lt; 5 » côté
 * interface) ; zéro reste zéro, car il ne désigne personne. Classe pure : aucune dépendance Spring ni JPA.
 */
public final class AnonymityPolicy {

    /**
     * Seuil d'effectif minimal (k-anonymat) : en dessous, le chiffre est masqué.
     */
    public static final int THRESHOLD = 5;

    private AnonymityPolicy() {
    }

    /**
     * @return l'effectif, ou {@code null} s'il est trop faible pour être publié
     */
    public static Long mask(long count) {
        return count > 0 && count < THRESHOLD ? null : count;
    }
}
