package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Forfait retenu pour une séance (RG-FAC-021) : son prix hors taxe est celui de la ligne de facture. Un forfait sans prix vaut
 * 0 ; {@link #NON_DEFINI} est le forfait d'une séance dont ni la surcharge ni la prise en charge n'en donnent un.
 */
public record ForfaitApplique(UUID id, String libelle, BigDecimal prixHt) {

    public static final ForfaitApplique NON_DEFINI = new ForfaitApplique(null, "Forfait non defini", BigDecimal.ZERO);

    public ForfaitApplique {
        prixHt = prixHt == null ? BigDecimal.ZERO : prixHt;
    }

    /**
     * Vrai si un forfait a été retenu (surcharge ou prise en charge) ; faux pour {@link #NON_DEFINI}, que la validation refuse
     * (KB-FAC-32).
     */
    public boolean estDefini() {
        return id != null;
    }

    /**
     * Clé de regroupement en ligne de facture : même forfait et même libellé.
     */
    String cleDeLigne() {
        return cleDeForfait() + "::" + libelle;
    }

    String cleDeForfait() {
        return id == null ? "NO_FORFAIT" : id.toString();
    }
}
