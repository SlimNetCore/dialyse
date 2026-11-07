package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Issue de l'analyse. Règle de positivité : seul un résultat POSITIF impose de documenter la conduite à tenir ; DOUTEUX et EN_COURS
 * passent sans conduite (reproduit, KB-MED-53, à confirmer avec les métiers et le DPO).
 */
public enum ResultatSerologique {
    POSITIF, NEGATIF, DOUTEUX, EN_COURS;

    /** Vrai si ce résultat ne peut être enregistré sans conduite à tenir. */
    public boolean exigeUneConduiteATenir() {
        return this == POSITIF;
    }

    /** Lit le résultat saisi : absent ou inconnu, c'est une saisie invalide (400 stable, KB-MED-47 et 48 corrigés). */
    public static ResultatSerologique lire(String saisie) {
        if (saisie == null) throw new SaisieInvalide("Le résultat de la sérologie est requis");
        try {
            return valueOf(saisie);
        } catch (IllegalArgumentException inconnu) {
            throw new SaisieInvalide("Résultat de sérologie inconnu");
        }
    }
}
