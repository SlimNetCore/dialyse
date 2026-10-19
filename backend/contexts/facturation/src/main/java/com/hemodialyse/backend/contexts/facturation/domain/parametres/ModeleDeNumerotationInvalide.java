package com.hemodialyse.backend.contexts.facturation.domain.parametres;

/**
 * Le modèle de numérotation saisi ne produirait pas de numéros de facture valides (400, KB-FAC-09 corrigé).
 */
public class ModeleDeNumerotationInvalide extends IllegalArgumentException {
    ModeleDeNumerotationInvalide(String message) {
        super(message);
    }
}
