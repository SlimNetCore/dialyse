package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Port sortant : les volets saisis pendant la séance (données cliniques, tenues par le legacy jusqu'à leur reprise). Lecture seule ;
 * seuls les champs affichés par le détail de la séance sont lus.
 */
public interface VoletsDeLaSeance {
    Optional<VoletParamedical> paramedical(CentreId centre, SeanceId seance);

    Optional<VoletMedical> medical(CentreId centre, SeanceId seance);

    record VoletParamedical(BigDecimal poidsAvantKg, BigDecimal poidsApresKg, String tensionAvant, String tensionApres,
                            Integer dureeMinutes, Integer debitSangMlMin, BigDecimal ultrafiltrationMl, String anticoagulant,
                            String typeDeDialysat, String incidents) {
    }

    record VoletMedical(String prescription, String toleranceDeLaSeance, String examenClinique, String resultatsBiologiques,
                        String ajustementsTherapeutiques, String conclusionMedicale) {
    }
}
