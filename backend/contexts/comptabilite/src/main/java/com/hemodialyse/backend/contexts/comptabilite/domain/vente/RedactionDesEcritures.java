package com.hemodialyse.backend.contexts.comptabilite.domain.vente;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.AxeAnalytique;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.LigneEcriture;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.NumeroDePiece;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.StatutEcriture;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.OperationComptable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service de domaine pur : rédige l'écriture de ventes d'une facture (R17) et l'écriture d'un règlement (R19), sans
 * aucun accès aux données. Libellés, ordre des lignes et arrondis reproduisent le comportement historique (filet C5).
 */
public final class RedactionDesEcritures {

    /**
     * Compte de TVA collectée lu quand le mapping n'en porte pas (KB-CPT-14).
     */
    static final String TVA_COLLECTEE_PAR_DEFAUT = "44571";

    private RedactionDesEcritures() {
    }

    /**
     * Débit du compte client = TTC ; crédit des ventes = HT ; crédit de la TVA collectée si TVA &gt; 0, la TVA d'une
     * facture ≤ 0 étant dérivée de TTC − HT quand ce reste est positif. Statut VALIDEE ; source = la facture.
     */
    public static EcritureComptable ventes(CentreId centre, MappingComptable mapping, String compteClient,
                                           FactureAComptabiliser facture, LocalDate date, NumeroDePiece numero) {
        List<AxeAnalytique> axes = axesDuCentre(centre);
        List<LigneEcriture> lignes = new ArrayList<>();
        lignes.add(new LigneEcriture(UUID.randomUUID(), compteClient,
                "Facture " + facture.numero() + " - " + facture.libelle(), facture.totalTtc(), BigDecimal.ZERO,
                facture.payeur(), axes));
        BigDecimal tva = facture.totalTva() != null ? facture.totalTva() : BigDecimal.ZERO;
        if (tva.signum() <= 0 && facture.totalTtc() != null && facture.totalHt() != null) {
            BigDecimal derivee = facture.totalTtc().subtract(facture.totalHt()).setScale(2, RoundingMode.HALF_UP);
            if (derivee.signum() > 0) {
                tva = derivee;
            }
        }
        lignes.add(new LigneEcriture(UUID.randomUUID(), mapping.compteVentes(), "Prestation dialyse - " + facture.numero(),
                BigDecimal.ZERO, facture.totalHt(), null, axes));
        if (tva.signum() > 0) {
            lignes.add(new LigneEcriture(UUID.randomUUID(),
                    mapping.compteTvaCollectee().orElse(TVA_COLLECTEE_PAR_DEFAUT), "TVA collectée - " + facture.numero(),
                    BigDecimal.ZERO, tva, null, List.of()));
        }
        return EcritureComptable.reconstituer(UUID.randomUUID(), centre, mapping.journalDe(OperationComptable.VENTE),
                date, date, numero, "Fact. " + facture.numero() + " - " + facture.libelle(), lignes, StatutEcriture.VALIDEE,
                facture.id(), null, null, null);
    }

    /**
     * Débit de la banque ou de la caisse, crédit du compte client, pour la valeur absolue du montant (B6). Statut
     * VALIDEE ; source = le règlement.
     */
    public static EcritureComptable reglement(CentreId centre, MappingComptable mapping, String compteClient,
                                              ReglementAComptabiliser reglement, NumeroDePiece numero) {
        List<AxeAnalytique> axes = axesDuCentre(centre);
        BigDecimal montant = reglement.montant().abs();
        String tresorerie = reglement.enCaisse() ? mapping.compteCaisse() : mapping.compteBanque();
        String libelle = "Règlement " + reglement.tiersLibelle();
        List<LigneEcriture> lignes = List.of(
                new LigneEcriture(UUID.randomUUID(), tresorerie, libelle, montant, BigDecimal.ZERO, null, axes),
                new LigneEcriture(UUID.randomUUID(), compteClient, "Règlement facture " + reglement.facture(),
                        BigDecimal.ZERO, montant, reglement.payeur(), axes));
        return EcritureComptable.reconstituer(UUID.randomUUID(), centre, journalDuReglement(mapping, reglement),
                reglement.date(), reglement.date(), numero, libelle, lignes, StatutEcriture.VALIDEE, reglement.id(), null,
                null, null);
    }

    public static com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal journalDuReglement(
            MappingComptable mapping, ReglementAComptabiliser reglement) {
        return mapping.journalDe(reglement.enCaisse() ? OperationComptable.REGLEMENT_CAISSE
                : OperationComptable.REGLEMENT_BANQUE);
    }

    /**
     * Compte client d'une facture : celui du payeur s'il en a un, sinon le compte client par défaut ; sans payeur, le
     * compte client des patients (R18).
     */
    public static String compteClient(MappingComptable mapping, UUID payeur, java.util.Optional<String> compteDuPayeur) {
        if (payeur == null) return mapping.compteClientPatient();
        return compteDuPayeur.orElse(mapping.compteClientDefaut());
    }

    /**
     * Le compte que l'écriture de la facture a débité (premier débit non nul), à solder par le règlement.
     */
    public static java.util.Optional<String> compteDebite(EcritureComptable venteDeLaFacture) {
        return venteDeLaFacture.lignes().stream().filter(l -> l.debit().signum() > 0).map(LigneEcriture::compte).findFirst();
    }

    private static List<AxeAnalytique> axesDuCentre(CentreId centre) {
        return List.of(new AxeAnalytique("CENTRE", "Centre " + centre.valeur()));
    }
}

