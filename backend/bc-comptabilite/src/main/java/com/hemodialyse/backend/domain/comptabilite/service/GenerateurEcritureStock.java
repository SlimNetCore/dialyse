package com.hemodialyse.backend.domain.comptabilite.service;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.entity.LigneEcriture;
import com.hemodialyse.backend.domain.comptabilite.port.OperationsStockPort.Ecart;
import com.hemodialyse.backend.domain.comptabilite.port.OperationsStockPort.Montant;
import com.hemodialyse.backend.domain.comptabilite.valueobject.AxeAnalytique;
import com.hemodialyse.backend.domain.comptabilite.valueobject.ComptesStock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Schémas d'écriture du stock en inventaire permanent — pur, sans I/O.
 * <ul>
 *   <li><b>Réception</b> : débit stock / crédit factures non parvenues (hors taxe : la TVA et le compte fournisseur se
 *   traitent à la facture, hors application) ;</li>
 *   <li><b>Sortie</b> : débit consommation / crédit stock, au prix moyen pondéré appliqué à la sortie ;</li>
 *   <li><b>Inventaire</b> : excédent = débit stock / crédit boni ; manquant = débit mali / crédit stock.</li>
 * </ul>
 * Chaque schéma est d'abord exprimé en <b>soldes par compte</b> (positif = débit, négatif = crédit), ce qui permet de
 * comparer ce qui devrait être comptabilisé à ce qui l'est déjà, puis de n'écrire que la différence.
 */
public final class GenerateurEcritureStock {

    private GenerateurEcritureStock() {
    }

    /**
     * Soldes d'une réception : stock au débit (compte de l'article ou du centre), factures non parvenues au crédit.
     */
    public static Map<String, BigDecimal> soldesReception(List<Montant> lignes, ComptesStock comptes) {
        Map<String, BigDecimal> soldes = new TreeMap<>();
        for (Montant m : lignes) {
            BigDecimal montant = arrondi(m.montant());
            ajouter(soldes, comptes.stockDe(m.compteStockArticle()), montant);
            ajouter(soldes, comptes.facturesNonParvenues(), montant.negate());
        }
        return sansNuls(soldes);
    }

    /**
     * Soldes des sorties d'une journée : consommation au débit, stock au crédit.
     */
    public static Map<String, BigDecimal> soldesSorties(List<Montant> lignes, ComptesStock comptes) {
        Map<String, BigDecimal> soldes = new TreeMap<>();
        for (Montant m : lignes) {
            BigDecimal montant = arrondi(m.montant());
            ajouter(soldes, comptes.consommationDe(m.compteChargeArticle()), montant);
            ajouter(soldes, comptes.stockDe(m.compteStockArticle()), montant.negate());
        }
        return sansNuls(soldes);
    }

    /**
     * Soldes des écarts d'un inventaire : excédents en boni, manquants en mali.
     */
    public static Map<String, BigDecimal> soldesInventaire(List<Ecart> ecarts, ComptesStock comptes) {
        Map<String, BigDecimal> soldes = new TreeMap<>();
        for (Ecart e : ecarts) {
            String stock = comptes.stockDe(e.compteStockArticle());
            BigDecimal boni = arrondi(e.boni());
            BigDecimal mali = arrondi(e.mali());
            ajouter(soldes, stock, boni.subtract(mali));
            ajouter(soldes, comptes.boniInventaire(), boni.negate());
            ajouter(soldes, comptes.maliInventaire(), mali);
        }
        return sansNuls(soldes);
    }

    /**
     * Soldes déjà comptabilisés par un ensemble d'écritures.
     */
    public static Map<String, BigDecimal> soldesDe(List<EcritureComptable> ecritures) {
        Map<String, BigDecimal> soldes = new TreeMap<>();
        for (EcritureComptable e : ecritures) {
            for (LigneEcriture l : e.getLignes()) {
                ajouter(soldes, l.getCompteSCF(), l.getMontantDebit().subtract(l.getMontantCredit()));
            }
        }
        return sansNuls(soldes);
    }

    /**
     * {@code cible − deja} : ce qu'il reste à écrire (vide si tout est déjà comptabilisé).
     */
    public static Map<String, BigDecimal> difference(Map<String, BigDecimal> cible, Map<String, BigDecimal> deja) {
        Map<String, BigDecimal> reste = new TreeMap<>(cible);
        deja.forEach((compte, solde) -> ajouter(reste, compte, solde.negate()));
        return sansNuls(reste);
    }

    /**
     * Lignes d'écriture d'un jeu de soldes : débit pour un solde positif, crédit pour un solde négatif.
     */
    public static List<LigneEcriture> lignes(Map<String, BigDecimal> soldes, String libelle, UUID centerId) {
        List<AxeAnalytique> axes = List.of(AxeAnalytique.centre(centerId.toString()));
        List<LigneEcriture> lignes = new ArrayList<>();
        soldes.forEach((compte, solde) -> lignes.add(solde.signum() > 0
                ? LigneEcriture.debit(compte, libelle, solde, null, axes)
                : LigneEcriture.credit(compte, libelle, solde.negate(), null, axes)));
        return lignes;
    }

    private static void ajouter(Map<String, BigDecimal> soldes, String compte, BigDecimal montant) {
        soldes.merge(compte, montant, BigDecimal::add);
    }

    private static Map<String, BigDecimal> sansNuls(Map<String, BigDecimal> soldes) {
        soldes.values().removeIf(v -> v.signum() == 0);
        return soldes;
    }

    private static BigDecimal arrondi(BigDecimal montant) {
        return montant == null ? BigDecimal.ZERO.setScale(2) : montant.setScale(2, RoundingMode.HALF_UP);
    }
}
