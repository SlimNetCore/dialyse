package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PeriodeDuCahier;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port sortant (DOC5a, ADR 0193) : les séances du patient que le cahier imprime (statut VALIDEE, SIGNEE ou FACTUREE, casse exacte, dans la
 * période bornes incluses), de la plus récente à la plus ancienne puis par identifiant, avec les valeurs BRUTES que la Séance tient elle-même :
 * le forfait imposé et les deux volets (le volet médical n'est lu que s'il est visible : KB-PL5-4). Un volet est cherché sous le centre de la
 * séance (comme l'ancien gabarit).
 */
public interface SeancesImprimablesDuCahier {

    List<SeanceDuCahier> lister(CentreId centre, PatientId patient, PeriodeDuCahier periode, boolean voletMedicalVisible);

    /** Forfait imposé : identifiant du référentiel et valeurs recopiées sur la séance (code et nom éventuellement absents). */
    record ForfaitImpose(UUID forfait, String code, String nom, BigDecimal prix) {
    }

    record VoletParamedicalBrut(BigDecimal poidsAvantKg, BigDecimal poidsApresKg, String tensionAvant, String tensionApres,
                                Integer dureeMinutes, Integer debitSangMlMin, BigDecimal ultrafiltrationMl, String typeDeDialysat,
                                String anticoagulant, String incidents) {
    }

    record VoletMedicalBrut(String prescription, String tolerance, String examenClinique, String resultatsBiologiques,
                            String ajustementsTherapeutiques, String conclusionMedicale) {
    }

    /** Volets et forfait imposé : {@code null} s'ils sont absents. */
    record SeanceDuCahier(SeanceId seance, LocalDate jour, String statut, ForfaitImpose forfaitImpose, VoletParamedicalBrut paramedical,
                          VoletMedicalBrut medical) {
    }
}
