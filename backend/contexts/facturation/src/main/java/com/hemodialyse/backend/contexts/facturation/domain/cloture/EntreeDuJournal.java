package com.hemodialyse.backend.contexts.facturation.domain.cloture;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Une ligne du journal des clôtures et réouvertures d'un mois (append-only) : qui, quand, et pour une réouverture, pourquoi.
 *
 * @param seancesEligiblesRestantes renseigné pour une clôture seulement
 * @param motif                     renseigné pour une réouverture seulement
 */
public record EntreeDuJournal(Type type, CentreId centre, YearMonth mois, Instant le, UUID par, String motif,
                              Integer seancesEligiblesRestantes) {

    public static final int MOTIF_MAX = 500;

    public enum Type { CLOTURE, REOUVERTURE }

    static EntreeDuJournal cloture(CentreId centre, YearMonth mois, Instant le, UUID par, int seancesEligiblesRestantes) {
        return new EntreeDuJournal(Type.CLOTURE, centre, mois, le, par, null, seancesEligiblesRestantes);
    }

    static EntreeDuJournal reouverture(CentreId centre, YearMonth mois, Instant le, UUID par, String motif) {
        return new EntreeDuJournal(Type.REOUVERTURE, centre, mois, le, par, motif, null);
    }
}
