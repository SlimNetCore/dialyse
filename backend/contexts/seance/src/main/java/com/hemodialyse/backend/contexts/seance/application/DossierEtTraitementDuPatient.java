package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Port sortant (DOC5a, ADR 0193) : ce que le cahier imprime du dossier médical du patient et de son traitement en vigueur un jour de
 * séance. Propriété du Dossier médical (langage publié {@code DossierEtTraitementDuCahier}) ; valeurs brutes, mises en forme par la Séance.
 */
public interface DossierEtTraitementDuPatient {

    Optional<Dossier> dossier(CentreId centre, PatientId patient);

    /** La prescription en vigueur le jour donné ; vide si aucune. */
    Optional<Traitement> traitementEnVigueur(CentreId centre, PatientId patient, LocalDate jour);

    record Dossier(String nephropathieInitiale, LocalDate dateMiseEnDialyse, String statutHepatiteB, String statutHepatiteC,
                   String observationGlobale, String conclusionMedicale) {
    }

    record Traitement(BigDecimal poidsSecCibleKg, Medicament epo, Medicament fer) {
    }

    /** {@code article} : libellé de l'article, à défaut son code ; nul si aucun ou inconnu du centre. */
    record Medicament(String article, Integer dose, String voie, Integer frequenceValeur, String frequenceUnite) {
    }
}
