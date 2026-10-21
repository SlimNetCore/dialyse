package com.hemodialyse.backend.contexts.comptabilite.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié (besoin de la Comptabilité, fourni par la Facturation) : les factures émises d'un centre, en lecture seule,
 * triées par date de facturation puis numéro. Les factures de reprise des soldes d'ouverture ({@code REPRISE-…}) n'en font
 * pas partie : elles sont comptabilisées à part (KB-FAC-67, décision du 2026-10-21). Un total absent vaut zéro.
 * Le sens de la dépendance (la Facturation connaît la Comptabilité) impose que ce port soit publié ici.
 */
public interface FacturesEmises {

    /** Factures dont la période facturée est incluse dans [debut, fin], sans filtre de statut. */
    List<FactureEmise> dontLaPeriodeEstIncluseDans(UUID centre, LocalDate debut, LocalDate fin);

    /** Factures émises (date de facturation) dans [du, au]. */
    List<FactureEmise> emisesEntre(UUID centre, LocalDate du, LocalDate au);

    /**
     * @param payeur       payeur figé sur la facture ; nul si le patient paie lui-même
     * @param finDePeriode dernier jour de la période facturée
     */
    record FactureEmise(UUID id, String numero, UUID payeur, BigDecimal totalHt, BigDecimal totalTva, BigDecimal totalTtc,
                        LocalDate dateFacturation, LocalDate finDePeriode, String libelle) {
    }
}
