package com.hemodialyse.backend.contexts.patientcouverture.application;

import java.util.List;

/** Page de la recherche : {@code items}, {@code total}, {@code page}, {@code size} (contrat JSON actuel). */
public record PageDePatients(List<LignePatient> items, long total, int page, int size) {
}
