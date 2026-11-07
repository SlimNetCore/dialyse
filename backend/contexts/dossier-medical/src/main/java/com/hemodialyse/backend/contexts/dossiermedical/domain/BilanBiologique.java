package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Un bilan biologique tel que le suivi de l'anémie le lit (hémoglobine, ferritine, CST, albumine, Kt/V mensuel). */
public record BilanBiologique(LocalDate date, BigDecimal hemoglobine, BigDecimal ferritine, BigDecimal cst, BigDecimal albumine,
                              BigDecimal ktV) {
}
