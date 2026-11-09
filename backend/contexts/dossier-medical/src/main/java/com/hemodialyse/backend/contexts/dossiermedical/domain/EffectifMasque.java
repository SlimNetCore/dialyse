package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Effectif masqué (glossaire) : dans les indicateurs cliniques publiés à la Direction, tout effectif de patients compris entre 1 et
 * {@link #SEUIL} - 1 est remplacé par {@code null} (k-anonymat) ; zéro reste zéro, car il ne désigne personne. Même seuil que la politique
 * d'anonymat de la plateforme pour les autres tableaux de la Direction (deux contextes, deux politiques : décision explicite, ADR 0101).
 * Reproduit (KB-MED-104, à trancher par le DPO) : le masque s'applique APRÈS la consolidation des valeurs brutes des centres.
 */
public final class EffectifMasque {

    public static final int SEUIL = 5;

    private EffectifMasque() {
    }

    /** @return l'effectif, ou {@code null} s'il est trop faible pour être publié */
    public static Long masquer(long effectif) {
        return effectif > 0 && effectif < SEUIL ? null : effectif;
    }

    public static boolean sousLeSeuil(long effectif) {
        return effectif < SEUIL;
    }
}
