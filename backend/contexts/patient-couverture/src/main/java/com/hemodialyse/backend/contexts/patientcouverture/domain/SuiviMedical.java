package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.time.LocalDate;

/** Indicateurs administratifs de suivi saisis sur la fiche (sous KT, EPO, fer, sommeil), observation et photo. */
public record SuiviMedical(boolean sousKt, boolean epoActive, LocalDate epoDate, boolean ferActif, LocalDate ferDate,
                           boolean enSommeil, String observation, String photoBase64) {
}
