package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import java.math.BigDecimal;

/**
 * Prix d'acquisition d'un équipement, positif ou nul. L'échelle reçue est conservée telle quelle (KB-GMAO-35 : la réponse
 * d'un enregistrement reprend l'échelle envoyée alors que la base arrondit à deux décimales) ; le domaine n'arrondit pas.
 */
public record PrixAcquisition(BigDecimal valeur) {

    public PrixAcquisition {
        if (valeur == null) throw new IllegalArgumentException("Prix d'acquisition requis");
        if (valeur.signum() < 0) throw new PrixNegatif();
    }

    /** Pas de prix : {@code null}. */
    public static PrixAcquisition ouAucun(BigDecimal valeur) {
        return valeur == null ? null : new PrixAcquisition(valeur);
    }
}
