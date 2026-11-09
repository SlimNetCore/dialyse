package com.hemodialyse.backend.contexts.pilotagedirection.domain;

/**
 * Le seuil d'anonymat des tableaux de bord de la direction : seuls des agrégats sont publiés, et tout effectif de patients compris entre 1
 * et {@value #SEUIL} - 1 est masqué (publié {@code null}, imprimé « &lt; seuil ») parce qu'il permettrait de reconnaître des patients d'un
 * petit centre ou d'un petit groupe. Zéro reste zéro : il ne désigne personne.
 * <p>
 * Politique PROPRE au Pilotage (décision ADR 0101 point 4 : deux contextes, deux politiques, mêmes valeurs que {@code EffectifMasque} du
 * Dossier médical). Les totaux de la société sont calculés sur les effectifs bruts (KB-DIR-01, reproduit).
 */
public final class SeuilDAnonymat {

    public static final int SEUIL = 5;

    private SeuilDAnonymat() {
    }

    /** L'effectif publiable, ou {@code null} s'il est trop faible pour être publié. */
    public static Long masquer(long effectif) {
        return effectif > 0 && effectif < SEUIL ? null : effectif;
    }
}
