package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Corps de l'enregistrement du dossier médical de base (mêmes champs que le legacy). Tout champ absent est effacé. */
public record DossierMedicalDeBaseDemande(UUID centerId, String nephropathieInitiale, LocalDate dateMiseEnDialyse, String hepatiteBStatut,
                                          String hepatiteCStatut, String observationGlobale, String conclusionMedicale) {
}
