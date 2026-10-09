package com.hemodialyse.backend.domain.comptabilite.valueobject;

/**
 * Comptes du stock d'un centre (inventaire permanent). Le compte de stock et le compte de consommation sont les valeurs
 * par défaut : la fiche d'un article peut préciser les siens (médicaments, consommables…).
 *
 * @param stock                compte de stock débité à la réception, crédité à la sortie (ex. 322)
 * @param consommation         compte de charge débité à la sortie (ex. 602)
 * @param facturesNonParvenues contrepartie d'une réception sans facture (ex. 408)
 * @param boniInventaire       produit constaté quand l'inventaire trouve plus que le stock théorique (ex. 757)
 * @param maliInventaire       charge constatée quand l'inventaire trouve moins (ex. 657)
 */
public record ComptesStock(String stock, String consommation, String facturesNonParvenues, String boniInventaire,
                           String maliInventaire) {

    public ComptesStock {
        stock = exiger(stock, "de stock");
        consommation = exiger(consommation, "de consommation");
        facturesNonParvenues = exiger(facturesNonParvenues, "des factures non parvenues");
        boniInventaire = exiger(boniInventaire, "de boni d'inventaire");
        maliInventaire = exiger(maliInventaire, "de mali d'inventaire");
    }

    public static ComptesStock parDefaut() {
        return new ComptesStock("322", "602", "408", "757", "657");
    }

    private static String exiger(String compte, String nom) {
        if (compte == null || compte.isBlank()) {
            throw new IllegalArgumentException("Le compte " + nom + " est obligatoire");
        }
        return compte.trim();
    }

    /**
     * Compte de stock d'un article : le sien s'il en a un, sinon celui du centre.
     */
    public String stockDe(String compteArticle) {
        return compteArticle == null || compteArticle.isBlank() ? stock : compteArticle.trim();
    }

    /**
     * Compte de consommation d'un article : le sien s'il en a un, sinon celui du centre.
     */
    public String consommationDe(String compteArticle) {
        return compteArticle == null || compteArticle.isBlank() ? consommation : compteArticle.trim();
    }
}
