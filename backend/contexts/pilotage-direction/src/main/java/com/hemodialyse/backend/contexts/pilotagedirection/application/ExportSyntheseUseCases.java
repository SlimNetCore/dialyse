package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.ExportSynthese;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ExportSyntheseId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.FormatExport;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;

/**
 * Port d'entrée (use cases) de l'entité {@code ExportSynthese} (slice PD4).
 * Cas d'usage : {@code GenererExportSynthese}, {@code TelechargerExport}.
 */
public interface ExportSyntheseUseCases {

    ExportSyntheseId generer(GenererExportCommande commande, Instant maintenant);

    ExportSynthese telecharger(ExportSyntheseId exportId, CentreId centreId);

    record GenererExportCommande(CentreId centreId, String periode, FormatExport format) {
    }
}
