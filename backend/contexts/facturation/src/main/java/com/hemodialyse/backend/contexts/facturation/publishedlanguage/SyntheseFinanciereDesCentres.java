package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Langage publié (lecture) : la part financière de la Facturation pour le pilotage de la Direction, sur une période (bornes
 * incluses, date de facturation). Montants enregistrés sur les factures, jamais recalculés. L'encaissé est la somme signée
 * des règlements de toutes dates des factures émises dans la période (KB-FAC-62 reproduit). Un centre sans facture est
 * absent des résultats. Aucune isolation de société ici : l'appelant fournit les centres de la société.
 */
public interface SyntheseFinanciereDesCentres {

    /** Chiffres de chaque centre qui a au moins une facture émise dans la période. */
    Map<UUID, ChiffresDuCentre> chiffresParCentre(Collection<UUID> centres, LocalDate du, LocalDate au);

    /** Le chiffre d'affaires de chaque mois et de chaque centre (date de facturation). */
    List<ChiffresDuMois> chiffresParMois(Collection<UUID> centres, LocalDate du, LocalDate au);

    /** Le chiffre d'affaires HT par centre et par caisse (agence figée sur la facture ; code vide sans caisse). */
    List<ChiffreDeLaCaisse> chiffreDAffairesParCaisse(Collection<UUID> centres, LocalDate du, LocalDate au);

    /** Les chiffres cumulés de tous les centres demandés (zéro sans facture). */
    default ChiffresDuCentre chiffresCumules(Collection<UUID> centres, LocalDate du, LocalDate au) {
        long factures = 0;
        BigDecimal ht = BigDecimal.ZERO;
        BigDecimal ttc = BigDecimal.ZERO;
        BigDecimal encaisse = BigDecimal.ZERO;
        for (ChiffresDuCentre c : chiffresParCentre(centres, du, au).values()) {
            factures += c.factures();
            ht = ht.add(c.chiffreDAffairesHt());
            ttc = ttc.add(c.chiffreDAffairesTtc());
            encaisse = encaisse.add(c.encaisse());
        }
        return new ChiffresDuCentre(factures, ht, ttc, encaisse);
    }

    record ChiffresDuCentre(long factures, BigDecimal chiffreDAffairesHt, BigDecimal chiffreDAffairesTtc, BigDecimal encaisse) {
    }

    record ChiffresDuMois(UUID centre, int annee, int mois, BigDecimal chiffreDAffairesHt, BigDecimal chiffreDAffairesTtc) {
    }

    record ChiffreDeLaCaisse(UUID centre, String codeCaisse, String nomCaisse, BigDecimal chiffreDAffairesHt) {
    }
}
