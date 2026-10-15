package com.hemodialyse.backend.contexts.comptabilite.domain.mapping;

/**
 * Comptes du stock d'un centre, en inventaire permanent (RG-CPT-011). Le compte de stock et celui de consommation sont les
 * valeurs par défaut : la fiche d'un article peut préciser les siens.
 *
 * @param stock                compte débité à la réception, crédité à la sortie (322)
 * @param consommation         charge débitée à la sortie (602)
 * @param facturesNonParvenues contrepartie d'une réception sans facture (408)
 * @param boniInventaire       produit constaté quand l'inventaire trouve plus que le stock théorique (757)
 * @param maliInventaire       charge constatée quand l'inventaire trouve moins (657)
 */
public record ComptesDuStock(String stock, String consommation, String facturesNonParvenues, String boniInventaire,
                             String maliInventaire) {

    public ComptesDuStock {
        stock = MappingComptable.compteObligatoire(stock, "de stock");
        consommation = MappingComptable.compteObligatoire(consommation, "de consommation");
        facturesNonParvenues = MappingComptable.compteObligatoire(facturesNonParvenues, "des factures non parvenues");
        boniInventaire = MappingComptable.compteObligatoire(boniInventaire, "de boni d'inventaire");
        maliInventaire = MappingComptable.compteObligatoire(maliInventaire, "de mali d'inventaire");
    }

    public static ComptesDuStock parDefaut() {
        return new ComptesDuStock("322", "602", "408", "757", "657");
    }
}
