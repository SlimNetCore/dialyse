package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.List;

/** Une page de demandes d'examen d'un patient. */
public record PageDeDemandesDExamen(List<DemandeExamen> items, long total, int page, int taille) {
}
