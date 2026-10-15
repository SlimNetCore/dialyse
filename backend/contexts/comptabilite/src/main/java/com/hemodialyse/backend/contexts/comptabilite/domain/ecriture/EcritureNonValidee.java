package com.hemodialyse.backend.contexts.comptabilite.domain.ecriture;

import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;

/**
 * Seule une écriture validée s'exporte ({@code EXPORT_ECRITURE_NON_VALIDEE}, 422).
 */
public class EcritureNonValidee extends RegleMetierViolee {
    EcritureNonValidee() {
        super("EXPORT_ECRITURE_NON_VALIDEE", "Seules les écritures validées peuvent être exportées");
    }
}
