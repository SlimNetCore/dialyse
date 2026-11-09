package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Effectif masqué (glossaire) : dans les indicateurs cliniques publiés à la Direction, tout effectif de patients compris entre 1 et
 * {@link #SEUIL} - 1 est remplacé par {@code null} (k-anonymat) ; zéro reste zéro, car il ne désigne personne. Même seuil que la politique
 * d'anonymat de la plateforme pour les autres tableaux de la Direction (deux contextes, deux politiques : décision explicite, ADR 0101).
 * Corrigé le 2026-11-09 (KB-MED-104, ADR 0112, validation DPO attendue) : le total de plusieurs centres est supprimé dès qu'un centre est
 * masqué ({@link #consolider}).
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

    /** Vrai pour un effectif de 1 à {@link #SEUIL} - 1 : celui que {@link #masquer(long)} remplace par {@code null}. */
    public static boolean estMasque(long effectif) {
        return effectif > 0 && effectif < SEUIL;
    }

    /**
     * Suppression complémentaire (KB-MED-104, corrigé le 2026-11-09) : le total de plusieurs centres est publié {@code null} dès que l'un
     * des effectifs bruts qui le composent est masqué, sinon le total moins la somme des centres publiés révélerait l'effectif masqué.
     * Un total sans centre ou composé de zéros reste zéro ; un total composé uniquement d'effectifs publiables reste masqué s'il est lui-même
     * sous le seuil (impossible sans effectif masqué, par construction).
     */
    public static Long consolider(java.util.Collection<Long> effectifsBrutsDesCentres) {
        long somme = 0;
        for (long effectif : effectifsBrutsDesCentres) {
            if (estMasque(effectif)) return null;
            somme += effectif;
        }
        return masquer(somme);
    }

    /** Vrai si au moins l'un des effectifs bruts des centres est masqué (le total correspondant est alors supprimé). */
    public static boolean composeDeMasques(java.util.Collection<Long> effectifsBrutsDesCentres) {
        return effectifsBrutsDesCentres.stream().anyMatch(EffectifMasque::estMasque);
    }
}
