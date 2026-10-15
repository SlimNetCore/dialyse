package com.hemodialyse.backend.contexts.comptabilite.domain.ecriture;

import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;

/**
 * Une écriture exportée ne se modifie plus : toute correction passe par une extourne ({@code ECRITURE_EXPORTEE_IMMUABLE}, 422).
 */
public class EcritureExporteeImmuable extends RegleMetierViolee {
    EcritureExporteeImmuable() {
        super("ECRITURE_EXPORTEE_IMMUABLE", "Une écriture exportée ne peut pas être modifiée");
    }
}
