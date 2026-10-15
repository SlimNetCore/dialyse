package com.hemodialyse.backend.contexts.comptabilite.domain.plan;

import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;

/**
 * Le compte est utilisé : il ne peut être ni désactivé ni supprimé (RG-CPT-018, {@code COMPTE_UTILISE}).
 */
public class CompteUtilise extends RegleMetierViolee {

    private CompteUtilise(String message) {
        super("COMPTE_UTILISE", message);
    }

    static CompteUtilise parLeParametrage(NumeroDeCompte compte, String action) {
        return new CompteUtilise("Le compte " + compte
                + " est utilisé par le paramétrage, un payeur ou un modèle de pièce : remplacez-le avant de le " + action);
    }

    static CompteUtilise parUnArticle(NumeroDeCompte compte, String action) {
        return new CompteUtilise("Le compte " + compte
                + " est rattaché à un article du stock : changez le compte de l'article avant de le " + action);
    }
}
