package com.hemodialyse.backend.contexts.facturation.application.reglement;

import com.hemodialyse.backend.contexts.facturation.domain.reglement.EtatDeReglement;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.TypeDeSolde;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Tableau de bord des règlements : agrégats par situation sur les factures du filtre. Reproduit le legacy tel quel (KB-FAC-45) :
 * {@code reglees} compte aussi les trop-perçus alors que la tranche {@code REGLE} ne compte que les factures soldées exactement ;
 * les tranches portent le montant <b>facturé</b>, sauf {@code TROP_PERCU} qui porte le trop-perçu ; libellés sans accents.
 */
public record TableauDeBord(UUID centerId, int year, Integer month, long totalFactures, long nonReglees,
                            long partiellementReglees, long reglees, long tropPercus, BigDecimal totalFacture,
                            BigDecimal totalRegle, BigDecimal totalReste, BigDecimal totalTropPercu,
                            List<Tranche> statusBreakdown) {

    public static TableauDeBord etablir(CriteresDeSituation criteres, List<LigneDeSituation> lignes) {
        long nonReglees = compter(lignes, ligne -> ligne.etat() == EtatDeReglement.NON_REGLEE);
        long partiellement = compter(lignes, ligne -> ligne.etat() == EtatDeReglement.PARTIELLEMENT_REGLEE);
        long reglees = compter(lignes, ligne -> ligne.etat() == EtatDeReglement.REGLEE);
        long tropPercus = compter(lignes, ligne -> ligne.soldeType() == TypeDeSolde.TROP_PERCU);
        BigDecimal totalTropPercu = somme(lignes, ligne -> true, LigneDeSituation::tropPercu);
        List<Tranche> tranches = List.of(
                new Tranche(EtatDeReglement.NON_REGLEE.name(), "Non reglee", nonReglees,
                        somme(lignes, ligne -> ligne.etat() == EtatDeReglement.NON_REGLEE, LigneDeSituation::montantFacture)),
                new Tranche(EtatDeReglement.PARTIELLEMENT_REGLEE.name(), "Partiellement reglee", partiellement,
                        somme(lignes, ligne -> ligne.etat() == EtatDeReglement.PARTIELLEMENT_REGLEE,
                                LigneDeSituation::montantFacture)),
                new Tranche(TypeDeSolde.REGLE.name(), "Reglee", compter(lignes, ligne -> ligne.soldeType() == TypeDeSolde.REGLE),
                        somme(lignes, ligne -> ligne.soldeType() == TypeDeSolde.REGLE, LigneDeSituation::montantFacture)),
                new Tranche(TypeDeSolde.TROP_PERCU.name(), "Trop percu", tropPercus, totalTropPercu));
        return new TableauDeBord(criteres.centre().valeur(), criteres.annee(), criteres.mois(), lignes.size(), nonReglees,
                partiellement, reglees, tropPercus, somme(lignes, ligne -> true, LigneDeSituation::montantFacture),
                somme(lignes, ligne -> true, LigneDeSituation::montantRegle),
                somme(lignes, ligne -> true, LigneDeSituation::reste), totalTropPercu, tranches);
    }

    private static long compter(List<LigneDeSituation> lignes, Predicate<LigneDeSituation> retenue) {
        return lignes.stream().filter(retenue).count();
    }

    private static BigDecimal somme(List<LigneDeSituation> lignes, Predicate<LigneDeSituation> retenue,
                                    Function<LigneDeSituation, BigDecimal> montant) {
        return lignes.stream().filter(retenue).map(montant)
                .map(m -> m == null ? BigDecimal.ZERO.setScale(2) : m.setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /**
     * Une tranche du tableau de bord (code, libellé, nombre de factures, montant).
     */
    public record Tranche(String code, String label, long count, BigDecimal amount) {
    }
}
