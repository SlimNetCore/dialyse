package com.hemodialyse.backend.domain.absence.service;

import com.hemodialyse.backend.domain.absence.model.ValeurAbsence;
import com.hemodialyse.backend.domain.absence.port.ForfaitAbsencePort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service de domaine : valorise une absence au forfait de la prise en charge du patient (prix HT, comme en facturation) ; le TTC
 * en découle avec le taux de TVA actif à la date de la séance manquée. Sans forfait, la valeur est nulle (jamais bloquant).
 */
public class ValorisationAbsenceService {

    private final ForfaitAbsencePort forfaits;

    public ValorisationAbsenceService(ForfaitAbsencePort forfaits) {
        this.forfaits = forfaits;
    }

    public ValeurAbsence valoriser(UUID centerId, UUID patientId, LocalDate date) {
        BigDecimal taux = forfaits.tauxTva(centerId, date);
        return forfaits.forfaitPriseEnCharge(centerId, patientId, date)
                .map(f -> ValeurAbsence.depuisHt(f.forfaitId(), f.libelle(), f.prixHt(), taux))
                .orElseGet(() -> ValeurAbsence.depuisHt(null, null, BigDecimal.ZERO, taux));
    }
}
