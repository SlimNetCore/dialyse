package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PeriodeDuCahier;

/**
 * Ce que la Séance demande à Documents d'imprimer : le cahier d'un patient sur une période, avec la ligne d'identité, l'édition (date et
 * auteur) et la portée de la lecture ({@code voletMedicalVisible} faux : ni hépatites, ni conclusions, ni prescription, KB-PL5-4).
 */
public record DemandeDeCahier(PatientId patient, PeriodeDuCahier periode, String ligneDIdentite, String editeLe, String editePar,
                              boolean voletMedicalVisible) {
}
