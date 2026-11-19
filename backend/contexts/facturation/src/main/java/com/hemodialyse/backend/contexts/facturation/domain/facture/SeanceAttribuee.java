package com.hemodialyse.backend.contexts.facturation.domain.facture;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Une séance d'une facture émise avec la ligne figée qui lui est attribuée (RG-FAC-048). {@code ligne} nulle : le forfait de la séance est
 * non attribuable (les lignes figées restent la référence des montants).
 *
 * @param seance identifiant de la séance
 * @param date   date de la séance
 * @param statut statut de la séance, tel que lu
 * @param ligne  ligne figée attribuée, {@code null} si non attribuable
 */
public record SeanceAttribuee(UUID seance, LocalDate date, String statut, LigneFacture ligne) {

    public boolean estAttribuee() {
        return ligne != null;
    }
}
