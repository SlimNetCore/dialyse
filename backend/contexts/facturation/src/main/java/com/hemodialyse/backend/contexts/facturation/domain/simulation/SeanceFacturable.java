package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Séance réalisée, non facturée, dans la période : une ligne potentielle de facture (RG-FAC-020), avec le forfait qui lui est
 * appliqué. Seule une séance validée ou signée est facturable ; la signature du médecin n'est pas exigée.
 */
public record SeanceFacturable(UUID id, PatientFacture patient, LocalDate date, StatutDeSeance statut,
                               ForfaitApplique forfait) {

    public SeanceFacturable {
        if (id == null || patient == null || date == null || forfait == null) {
            throw new IllegalArgumentException("Séance facturable incomplète");
        }
        if (statut == null || !statut.estFacturable()) {
            throw new IllegalArgumentException("Seule une séance validée ou signée est facturable");
        }
    }
}
