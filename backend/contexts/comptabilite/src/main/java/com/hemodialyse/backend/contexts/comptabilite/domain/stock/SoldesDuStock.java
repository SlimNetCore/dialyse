package com.hemodialyse.backend.contexts.comptabilite.domain.stock;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.AxeAnalytique;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.LigneEcriture;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.ComptesDuStock;
import com.hemodialyse.backend.contexts.comptabilite.domain.stock.OperationsDeStock.Ecart;
import com.hemodialyse.backend.contexts.comptabilite.domain.stock.OperationsDeStock.Montant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Service de domaine pur : schémas d'écriture du stock en inventaire permanent, exprimés en <b>soldes par compte</b>
 * (positif = débit, négatif = crédit) pour comparer ce qui devrait être comptabilisé à ce qui l'est déjà et n'écrire que
 * la différence.
 * <ul>
 *   <li>réception : débit stock / crédit factures non parvenues (hors taxe) ;</li>
 *   <li>sorties : débit consommation / crédit stock ;</li>
 *   <li>inventaire : excédent = débit stock / crédit boni ; manquant = débit mali / crédit stock.</li>
 * </ul>
 * Chaque montant est arrondi au centime (HALF_UP) avant d'être cumulé ; le compte d'un article remplace celui du centre.
 */
public final class SoldesDuStock {

    private SoldesDuStock() {
    }

    public static Map<String, BigDecimal> reception(List<Montant> lignes, ComptesDuStock comptes) {
        Map<String, BigDecimal> soldes = new TreeMap<>();
        for (Montant m : lignes) {
            BigDecimal montant = arrondi(m.montant());
            ajouter(soldes, ou(m.compteStockArticle(), comptes.stock()), montant);
            ajouter(soldes, comptes.facturesNonParvenues(), montant.negate());
        }
        return sansNuls(soldes);
    }

    public static Map<String, BigDecimal> sorties(List<Montant> lignes, ComptesDuStock comptes) {
        Map<String, BigDecimal> soldes = new TreeMap<>();
        for (Montant m : lignes) {
            BigDecimal montant = arrondi(m.montant());
            ajouter(soldes, ou(m.compteChargeArticle(), comptes.consommation()), montant);
            ajouter(soldes, ou(m.compteStockArticle(), comptes.stock()), montant.negate());
        }
        return sansNuls(soldes);
    }

    public static Map<String, BigDecimal> inventaire(List<Ecart> ecarts, ComptesDuStock comptes) {
        Map<String, BigDecimal> soldes = new TreeMap<>();
        for (Ecart e : ecarts) {
            BigDecimal boni = arrondi(e.boni());
            BigDecimal mali = arrondi(e.mali());
            ajouter(soldes, ou(e.compteStockArticle(), comptes.stock()), boni.subtract(mali));
            ajouter(soldes, comptes.boniInventaire(), boni.negate());
            ajouter(soldes, comptes.maliInventaire(), mali);
        }
        return sansNuls(soldes);
    }

    /**
     * Soldes déjà comptabilisés par ces écritures.
     */
    public static Map<String, BigDecimal> deja(List<EcritureComptable> ecritures) {
        Map<String, BigDecimal> soldes = new TreeMap<>();
        for (EcritureComptable e : ecritures) {
            for (LigneEcriture l : e.lignes()) {
                ajouter(soldes, l.compte(), l.debit().subtract(l.credit()));
            }
        }
        return sansNuls(soldes);
    }

    /**
     * {@code cible − deja} : ce qu'il reste à écrire (vide si tout est comptabilisé).
     */
    public static Map<String, BigDecimal> difference(Map<String, BigDecimal> cible, Map<String, BigDecimal> deja) {
        Map<String, BigDecimal> reste = new TreeMap<>(cible);
        deja.forEach((compte, solde) -> ajouter(reste, compte, solde.negate()));
        return sansNuls(reste);
    }

    /**
     * Lignes d'un jeu de soldes, dans l'ordre des comptes : débit si positif, crédit si négatif.
     *
     * @param identifiants fournit l'identifiant de chaque ligne (le domaine n'en tire pas)
     */
    public static List<LigneEcriture> lignes(Map<String, BigDecimal> soldes, String libelle, CentreId centre,
                                             Supplier<UUID> identifiants) {
        List<AxeAnalytique> axes = List.of(new AxeAnalytique("CENTRE", "Centre " + centre.valeur()));
        List<LigneEcriture> lignes = new ArrayList<>();
        soldes.forEach((compte, solde) -> lignes.add(solde.signum() > 0
                ? new LigneEcriture(identifiants.get(), compte, libelle, solde, BigDecimal.ZERO, null, axes)
                : new LigneEcriture(identifiants.get(), compte, libelle, BigDecimal.ZERO, solde.negate(), null, axes)));
        return lignes;
    }

    private static String ou(String compteArticle, String compteDuCentre) {
        return compteArticle == null || compteArticle.isBlank() ? compteDuCentre : compteArticle.trim();
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



