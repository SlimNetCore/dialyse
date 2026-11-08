package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.Seance;

/**
 * La séance déclarée : celle dont les consommables viennent d'être déclarés (« consommable déclaré » du glossaire), avec la fiche du
 * patient lue avant l'écriture (pour les diffusions).
 */
record SeanceDeclaree(Seance seance, FicheDuPatient patient) {
}
