package com.hemodialyse.backend.contexts.facturation.application.reglement;

import com.hemodialyse.backend.contexts.facturation.domain.reglement.EtatDeReglement;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.TypeDeSolde;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Faux read model des situations : des lignes posées telles quelles, filtrées par centre et période de facturation, dans
 * l'ordre de {@code GET /reglements} (date de facturation puis numéro décroissants).
 */
public final class SituationsDesFacturesFixes implements SituationsDesFactures {

    private record Posee(CentreId centre, LigneDeSituation ligne) {
    }

    private final List<Posee> lignes = new ArrayList<>();

    public LigneDeSituation poser(CentreId centre, String numero, String date, String nom, String prenom, String caisse,
                                  String montantFacture, String montantRegle, String code) {
        BigDecimal facture = new BigDecimal(montantFacture);
        BigDecimal regle = new BigDecimal(montantRegle);
        int signe = facture.compareTo(regle);
        EtatDeReglement etat = regle.signum() == 0 && facture.signum() > 0 ? EtatDeReglement.NON_REGLEE
                : signe > 0 ? EtatDeReglement.PARTIELLEMENT_REGLEE : EtatDeReglement.REGLEE;
        TypeDeSolde solde = signe > 0 ? TypeDeSolde.RESTE : signe == 0 ? TypeDeSolde.REGLE : TypeDeSolde.TROP_PERCU;
        LigneDeSituation ligne = new LigneDeSituation(UUID.nameUUIDFromBytes(numero.getBytes()), numero, "ASS-" + numero, nom,
                prenom, null, caisse, null, "Agence", null, "Payeur", LocalDate.parse(date), facture, regle,
                signe > 0 ? facture.subtract(regle) : BigDecimal.ZERO, signe < 0 ? regle.subtract(facture) : BigDecimal.ZERO, etat,
                solde, code, code == null ? 0 : 1);
        lignes.add(new Posee(centre, ligne));
        return ligne;
    }

    @Override
    public PageDeSituations rechercher(CriteresDeSituation criteres, int page, int taille) {
        List<LigneDeSituation> toutes = toutes(criteres);
        int debut = Math.min(page * taille, toutes.size());
        return new PageDeSituations(toutes.subList(debut, Math.min(debut + taille, toutes.size())), toutes.size(), page, taille);
    }

    @Override
    public List<LigneDeSituation> toutes(CriteresDeSituation criteres) {
        return lignes.stream()
                .filter(p -> p.centre().equals(criteres.centre()))
                .map(Posee::ligne)
                .filter(l -> !l.dateFacturation().isBefore(criteres.debut()) && !l.dateFacturation().isAfter(criteres.fin()))
                .sorted(Comparator.comparing(LigneDeSituation::dateFacturation).reversed()
                        .thenComparing(LigneDeSituation::numeroFacture, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    public Optional<LigneDeSituation> deLaFacture(CentreId centre, UUID facture) {
        return lignes.stream().filter(p -> p.centre().equals(centre) && p.ligne().factureId().equals(facture))
                .map(Posee::ligne).findFirst();
    }
}
