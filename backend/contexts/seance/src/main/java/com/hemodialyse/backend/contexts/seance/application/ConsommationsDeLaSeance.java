package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Port sortant : les consommables imputés sur la séance (le Stock décide des lots), valorisés, dans l'ordre du Stock. */
public interface ConsommationsDeLaSeance {
    List<Ligne> historique(CentreId centre, SeanceId seance);

    record Ligne(UUID article, String code, String libelle, String unite, BigDecimal quantite, BigDecimal valeurUnitaire,
                 BigDecimal totalValorise) {
    }
}
