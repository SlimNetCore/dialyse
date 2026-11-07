package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Langage publié (fourni à la Séance par la plateforme, ADR 0088) : les volets saisis pendant la séance, en lecture seule. */
public interface VoletsFournis {

    Optional<Paramedical> paramedical(UUID centre, UUID seance);

    Optional<Medical> medical(UUID centre, UUID seance);

    record Paramedical(BigDecimal poidsAvantKg, BigDecimal poidsApresKg, String tensionAvant, String tensionApres, Integer dureeMinutes,
                       Integer debitSangMlMin, BigDecimal ultrafiltrationMl, String anticoagulant, String typeDeDialysat,
                       String incidents) {
    }

    record Medical(String prescription, String toleranceDeLaSeance, String examenClinique, String resultatsBiologiques,
                   String ajustementsTherapeutiques, String conclusionMedicale) {
    }
}
