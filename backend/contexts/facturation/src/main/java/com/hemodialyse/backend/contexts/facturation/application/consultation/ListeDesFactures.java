package com.hemodialyse.backend.contexts.facturation.application.consultation;

import com.hemodialyse.backend.contexts.facturation.domain.facture.LigneFacture;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port sortant : lecture des factures émises du centre (lecture seule, bornée au centre). */
public interface ListeDesFactures {

    /**
     * Les factures du centre dont la date de facturation est dans la période (bornes incluses), sans autre filtre, triées date de facturation
     * décroissante, numéro décroissant, identifiant.
     */
    List<FactureListee> entre(CentreId centre, LocalDate du, LocalDate au);

    /** La facture du centre ; vide si inconnue ou d'un autre centre. */
    Optional<FactureListee> deLaFacture(CentreId centre, UUID facture);

    /** Les lignes figées de la facture du centre, triées libellé, prix unitaire, identifiant ; vide si inconnue ou d'un autre centre. */
    List<LigneFacture> lignes(CentreId centre, UUID facture);
}
