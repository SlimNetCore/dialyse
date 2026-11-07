package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant : le forfait courant d'une séance. Le forfait imposé sur la séance (surcharge, SE5) prime ; sinon celui de la prise en
 * charge qui couvre le jour (effective d'abord, sinon demandée), sinon la plus récente qui porte un forfait. Aucun : vide.
 */
public interface ForfaitsDeSeance {
    Optional<ForfaitCourant> courant(CentreId centre, SeanceId seance, PatientId patient, LocalDate jour);

    /**
     * @param nombreDeSeances  toujours nul pour une surcharge ; zéro pour un forfait de prise en charge (valeur historique du legacy)
     * @param misAJourLe       renseigné pour une surcharge seulement
     * @param misAJourPar      renseigné pour une surcharge seulement
     */
    record ForfaitCourant(UUID id, String code, String nom, BigDecimal prix, Integer nombreDeSeances, OffsetDateTime misAJourLe,
                          String misAJourPar, boolean surcharge) {
    }
}
