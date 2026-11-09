package com.hemodialyse.backend.contexts.pilotagedirection.domain;

/**
 * Le seuil d'anonymat des tableaux de bord de la direction : seuls des agrégats sont publiés, et tout effectif de patients compris entre 1
 * et {@value #SEUIL} - 1 est masqué (publié {@code null}, imprimé « &lt; seuil ») parce qu'il permettrait de reconnaître des patients d'un
 * petit centre ou d'un petit groupe. Zéro reste zéro : il ne désigne personne.
 * <p>
 * Politique PROPRE au Pilotage (décision ADR 0101 point 4 : deux contextes, deux politiques, mêmes valeurs que {@code EffectifMasque} du
 * Dossier médical). Un total est supprimé dès qu'un des effectifs qui le composent est masqué (KB-DIR-01, corrigé le 2026-11-09).
 */
public final class SeuilDAnonymat {

    public static final int SEUIL = 5;

    private SeuilDAnonymat() {
    }

    /** Vrai pour un effectif de 1 à {@value #SEUIL} - 1 : celui que {@link #masquer(long)} remplace par {@code null}. */
    public static boolean estMasque(long effectif) {
        return effectif > 0 && effectif < SEUIL;
    }

    /**
     * Suppression complémentaire appliquée à un contenu DÉJÀ publié (instantané mensuel figé avant la correction de KB-DIR-01) : le total
     * est conservé seulement si lui-même et chacun de ses centres sont publiés ; un centre publié {@code null} (masqué) supprime le total.
     * La règle se lit sur les valeurs publiées, sans jamais réécrire le contenu figé.
     */
    public static Long totalPublieDepuis(Long totalFige, java.util.Collection<Long> centresPublies) {
        if (totalFige == null) return null;
        return centresPublies.stream().anyMatch(java.util.Objects::isNull) ? null : totalFige;
    }

    /** L'effectif publiable, ou {@code null} s'il est trop faible pour être publié. */
    public static Long masquer(long effectif) {
        return effectif > 0 && effectif < SEUIL ? null : effectif;
    }
}
