package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.util.UUID;

/** Corps de la mise à jour des notes du bilan (mêmes champs que le legacy ; un texte absent est effacé). */
public record NotesDuBilanDemande(UUID centerId, String contreIndications, String conclusionNephrologue) {
}
