package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.List;

/** Une page de prescriptions d'un patient. */
public record PageDePrescriptions(List<PrescriptionMedicale> items, long total, int page, int taille) {
}
