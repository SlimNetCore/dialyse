package com.hemodialyse.backend.contexts.comptabilite.domain.plan;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.UsagesDuParametrage;

/**
 * Compte du plan comptable d'un centre : numéro, libellé, et s'il peut encore être choisi (RG-CPT-018). Un compte
 * désactivé reste lisible (ses écritures demeurent) mais ne peut plus être choisi. Un compte utilisé ne se désactive ni
 * ne se supprime ; un compte qui porte des écritures se désactive mais ne se supprime pas.
 */
public final class CompteComptable {

    public static final int LIBELLE_MAX = 150;

    private final CentreId centre;
    private final NumeroDeCompte numero;
    private String libelle;
    private boolean actif;

    private CompteComptable(CentreId centre, NumeroDeCompte numero, String libelle, boolean actif) {
        this.centre = centre;
        this.numero = numero;
        this.libelle = libelleValide(libelle);
        this.actif = actif;
    }

    /**
     * Un compte tel que le plan de départ ou la base le décrit (aucune règle d'usage : il existe déjà).
     */
    public static CompteComptable reconstitue(CentreId centre, NumeroDeCompte numero, String libelle, boolean actif) {
        return new CompteComptable(centre, numero, libelle, actif);
    }

    /**
     * Ouvre un compte ; refusé s'il est créé désactivé alors que le paramétrage l'utilise déjà.
     */
    public static CompteComptable ouvrir(CentreId centre, NumeroDeCompte numero, String libelle, boolean actif,
                                         UsagesDuParametrage usages) {
        CompteComptable compte = new CompteComptable(centre, numero, libelle, actif);
        compte.exigerDesactivationPermise(usages);
        return compte;
    }

    private static String libelleValide(String libelle) {
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé du compte est obligatoire");
        }
        String rogne = libelle.trim();
        if (rogne.length() > LIBELLE_MAX) {
            throw new IllegalArgumentException("Le libellé du compte dépasse " + LIBELLE_MAX + " caractères");
        }
        return rogne;
    }

    /**
     * Remplace le libellé et l'état ; la désactivation est refusée si le compte est utilisé.
     */
    public void redefinir(String libelle, boolean actif, UsagesDuParametrage usages) {
        String nouveauLibelle = libelleValide(libelle);
        if (!actif) {
            exigerNonUtilise(usages, "désactiver");
        }
        this.libelle = nouveauLibelle;
        this.actif = actif;
    }

    /**
     * Refuse la suppression d'un compte utilisé (prioritaire) ou qui porte des écritures.
     */
    public void exigerSuppressible(UsagesDuParametrage usages) {
        exigerNonUtilise(usages, "supprimer");
        if (usages.compteAvecEcritures(centre, numero)) {
            throw new CompteAvecEcritures(numero);
        }
    }

    private void exigerDesactivationPermise(UsagesDuParametrage usages) {
        if (!actif) {
            exigerNonUtilise(usages, "désactiver");
        }
    }

    private void exigerNonUtilise(UsagesDuParametrage usages, String action) {
        if (usages.compteUtilise(centre, numero)) {
            throw CompteUtilise.parLeParametrage(numero, action);
        }
        if (usages.compteRattacheAUnArticle(centre, numero)) {
            throw CompteUtilise.parUnArticle(numero, action);
        }
    }

    public CentreId centre() {
        return centre;
    }

    public NumeroDeCompte numero() {
        return numero;
    }

    public String libelle() {
        return libelle;
    }

    public boolean actif() {
        return actif;
    }
}
