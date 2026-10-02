package com.hemodialyse.backend.domain.absence.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de sortie : forfait (prix HT) de la prise en charge du patient à une date, et taux de TVA de l'hémodialyse
 * applicable ce jour-là.
 */
public interface ForfaitAbsencePort {

    Optional<ForfaitPec> forfaitPriseEnCharge(UUID centerId, UUID patientId, LocalDate date);

    /**
     * Taux de TVA (en %) du type « HEMODIALYSE » actif à la date ; 0 si aucune règle ou prestation exonérée.
     */
    BigDecimal tauxTva(UUID centerId, LocalDate date);

    record ForfaitPec(UUID forfaitId, String libelle, BigDecimal prixHt) {
    }
}
