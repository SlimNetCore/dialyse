package com.hemodialyse.backend.contexts.facturation.application.pilotage;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Tableau de bord de facturation d'un centre pour un mois (lecture seule). Les montants sont les totaux enregistrés sur les
 * factures émises dans le mois (date de facturation), jamais recalculés. Le nombre de séances facturées compte les séances au
 * statut {@code FACTUREE} dont la date tombe dans le mois, alors que les répartitions comptent les séances rattachées aux
 * factures du mois (KB-FAC-54 reproduit).
 */
public record TableauDeBordDuMois(
        UUID centre,
        YearMonth mois,
        BigDecimal chiffreDAffairesTtc,
        BigDecimal chiffreDAffairesHt,
        long seancesFacturees,
        long patientsFactures,
        long facturesEmises,
        List<RepartitionParCaisse> parCaisse,
        List<RepartitionParSituation> parSituation
) {

    /** Une caisse d'assurance (« INCONNU » sans caisse) : séances et patients des factures du mois. */
    public record RepartitionParCaisse(String code, String libelle, long seances, long patients) {
    }

    /** Une situation du patient figée sur la facture (« INCONNU » si absente). */
    public record RepartitionParSituation(String code, String libelle, long seances) {
    }
}
