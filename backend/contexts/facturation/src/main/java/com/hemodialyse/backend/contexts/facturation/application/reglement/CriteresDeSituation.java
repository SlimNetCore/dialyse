package com.hemodialyse.backend.contexts.facturation.application.reglement;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Filtre de la recherche, du tableau de bord et de l'export : les factures du centre dont la <b>date de facturation</b> est dans
 * l'année (ou le mois), éventuellement d'une caisse, d'une agence ou d'un centre payeur (instantané de la facture, sinon payeur
 * courant du patient). Bornes de l'année : 2000 à 3000.
 */
public record CriteresDeSituation(CentreId centre, int annee, Integer mois, UUID caisse, UUID agence, UUID centrePayeur) {

    public CriteresDeSituation {
        if (centre == null) {
            throw new IllegalArgumentException("Le centerId est obligatoire");
        }
        if (annee < 2000 || annee > 3000) {
            throw new IllegalArgumentException("L'annee doit etre comprise entre 2000 et 3000");
        }
        if (mois != null && (mois < 1 || mois > 12)) {
            throw new IllegalArgumentException("Le mois doit etre compris entre 1 et 12");
        }
    }

    public LocalDate debut() {
        return YearMonth.of(annee, mois != null ? mois : 1).atDay(1);
    }

    public LocalDate fin() {
        return mois != null ? YearMonth.of(annee, mois).atEndOfMonth() : LocalDate.of(annee, 12, 31);
    }
}
