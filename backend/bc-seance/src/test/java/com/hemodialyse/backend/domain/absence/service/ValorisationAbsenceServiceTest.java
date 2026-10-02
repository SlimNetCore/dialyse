package com.hemodialyse.backend.domain.absence.service;

import com.hemodialyse.backend.domain.absence.model.ValeurAbsence;
import com.hemodialyse.backend.domain.absence.port.ForfaitAbsencePort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ValorisationAbsenceServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 1);
    private final UUID centre = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();

    private ValorisationAbsenceService service(Optional<ForfaitAbsencePort.ForfaitPec> forfait, String taux) {
        ForfaitAbsencePort port = new ForfaitAbsencePort() {
            @Override
            public Optional<ForfaitPec> forfaitPriseEnCharge(UUID c, UUID p, LocalDate d) {
                return forfait;
            }

            @Override
            public BigDecimal tauxTva(UUID c, LocalDate d) {
                return new BigDecimal(taux);
            }
        };
        return new ValorisationAbsenceService(port);
    }

    @Test
    void ttcDeduitDuHtAvecLeTauxDeTva() {
        ValeurAbsence v = service(Optional.of(new ForfaitAbsencePort.ForfaitPec(UUID.randomUUID(), "F1",
                new BigDecimal("10000"))), "19").valoriser(centre, patient, DATE);
        assertThat(v.prixTtc()).isEqualByComparingTo("11900.00");
        assertThat(v.montantHt()).isEqualByComparingTo("10000.00");
        assertThat(v.tauxTva()).isEqualByComparingTo("19");
    }

    @Test
    void tvaNulleLaisseLeMontantInchange() {
        ValeurAbsence v = service(Optional.of(new ForfaitAbsencePort.ForfaitPec(UUID.randomUUID(), "F1",
                new BigDecimal("8000"))), "0").valoriser(centre, patient, DATE);
        assertThat(v.montantHt()).isEqualByComparingTo("8000.00");
    }

    @Test
    void sansForfaitLaValeurEstNulle() {
        ValeurAbsence v = service(Optional.empty(), "19").valoriser(centre, patient, DATE);
        assertThat(v.forfaitId()).isNull();
        assertThat(v.montantHt()).isEqualByComparingTo("0");
    }
}
