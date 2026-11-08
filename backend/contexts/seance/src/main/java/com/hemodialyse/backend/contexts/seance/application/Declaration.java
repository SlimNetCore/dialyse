package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.Seance;

/** La séance dont les consommables viennent d'être déclarés, avec la fiche du patient lue avant l'écriture (pour les diffusions). */
record Declaration(Seance seance, FicheDuPatient patient) {
}
