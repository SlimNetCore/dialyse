package com.hemodialyse.backend.contexts.pilotagedirection.domain;

/**
 * Le nombre d'entrées demandé pour l'historique des alertes, BORNÉ à [1, 200] : une demande hors bornes est ramenée à la borne, jamais
 * refusée (0 et -5 donnent 1, 500 donne 200).
 */
public record LimiteDHistorique(int valeur) {

    public static final int MIN = 1;
    public static final int MAX = 200;
    public static final int PAR_DEFAUT = 50;

    public LimiteDHistorique {
        if (valeur < MIN || valeur > MAX) {
            throw new IllegalArgumentException("Limite hors bornes : utiliser LimiteDHistorique.bornee");
        }
    }

    public static LimiteDHistorique bornee(int demandee) {
        return new LimiteDHistorique(Math.max(MIN, Math.min(demandee, MAX)));
    }
}
