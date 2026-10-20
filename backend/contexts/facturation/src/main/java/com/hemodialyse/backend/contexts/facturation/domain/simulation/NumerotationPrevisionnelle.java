package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ModeleDeNumerotation;

import java.time.LocalDate;

/**
 * Numéros prévisionnels d'une simulation (RG-FAC-022, KB-FAC-20 reproduit) : la séquence courante du centre pour l'année de la
 * date de facturation, plus 1, plus 2… dans l'ordre des factures. Rien n'est réservé : la séquence réelle ne bouge qu'à
 * l'émission des factures.
 *
 * @param sequenceCourante dernière séquence consommée de l'année (0 si le centre n'a encore rien émis)
 */
public record NumerotationPrevisionnelle(ModeleDeNumerotation modele, LocalDate dateDeFacturation,
                                         int sequenceCourante) {

    public NumerotationPrevisionnelle {
        if (modele == null || dateDeFacturation == null) {
            throw new IllegalArgumentException("Numérotation prévisionnelle incomplète");
        }
        if (sequenceCourante < 0) {
            throw new IllegalArgumentException("La séquence courante ne peut pas être négative");
        }
    }

    /**
     * Le numéro de la {@code rang}-ième facture de la simulation (à partir de 1).
     */
    public String numero(CentreId centre, int rang) {
        return modele.numero(centre, dateDeFacturation, sequenceCourante + rang);
    }
}
