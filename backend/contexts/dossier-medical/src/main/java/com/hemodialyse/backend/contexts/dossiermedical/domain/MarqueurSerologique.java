package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Ce que l'analyse recherche (VIH, hépatites B et C, syphilis, CMV, toxoplasmose, EBV). L'ordre est celui du legacy : la synthèse du
 * statut sérologique est triée dans cet ordre. Noms sensibles à la casse (reproduit : {@code vih_ac} est refusé).
 */
public enum MarqueurSerologique {
    VIH_AC, AG_HBS, AC_HBS, AC_HBC, AC_VHC, ARN_VHC, TPHA, CMV_IGG, CMV_IGM, TOXO_IGG, TOXO_IGM, EBV_IGG, EBV_IGM;

    /**
     * Lit le marqueur saisi : absent ou inconnu, c'est une saisie invalide (400 stable, sans nom de classe interne : KB-MED-47 et 48
     * corrigés, le legacy répondait 500 pour l'absent et exposait le nom de sa classe pour l'inconnu).
     */
    public static MarqueurSerologique lire(String saisie) {
        if (saisie == null) throw new SaisieInvalide("Le marqueur de la sérologie est requis");
        try {
            return valueOf(saisie);
        } catch (IllegalArgumentException inconnu) {
            throw new SaisieInvalide("Marqueur de sérologie inconnu");
        }
    }
}
