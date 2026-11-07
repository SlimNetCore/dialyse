package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.Seance;

/** Résultat de la création : la séance et la fiche du patient (générateur précisé), de quoi bâtir la réponse et les diffusions. */
public record SeanceEnregistree(Seance seance, FicheDuPatient patient) {
}
