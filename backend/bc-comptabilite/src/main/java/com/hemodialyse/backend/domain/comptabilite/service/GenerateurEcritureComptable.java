package com.hemodialyse.backend.domain.comptabilite.service;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.entity.LigneEcriture;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteUseCase.GenererEcritureFacturationCommand;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteUseCase.GenererEcritureReglementCommand;
import com.hemodialyse.backend.domain.comptabilite.valueobject.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service de génération pur — sans I/O, sans Spring, sans JPA.
 * Reçoit le mapping et la règle TVA résolus par les ports (aucune résolution ici).
 */
public class GenerateurEcritureComptable {

    /**
     * Génère l'écriture journal ventes pour une facture émise.
     * <p>
     * Structure : débit 411xxx (tiers payeur) / crédit 706 (produit prestation).
     * Si TVA active : crédit 44571 en plus du crédit 706.
     */
    public EcritureComptable genererEcritureFacturation(
            GenererEcritureFacturationCommand cmd,
            MappingComptable mapping,
            String numeroPiece,
            RegleTVA regleTVA) {

        TypeTiersPayeur typeTiers = resoudreTypeTiers(cmd.typeTiersPayeur());
        String compteClient = mapping.compteClient(typeTiers);

        List<LigneEcriture> lignes = new ArrayList<>();
        List<AxeAnalytique> axes = List.of(
                AxeAnalytique.centre(cmd.centerId().toString()),
                AxeAnalytique.typeTiers(typeTiers)
        );

        // Débit tiers payeur (créance)
        lignes.add(LigneEcriture.debit(
                compteClient,
                "Facture " + cmd.numeroFacture() + " - " + cmd.libelle(),
                cmd.totalTtc(),
                cmd.tiersPayeurId(),
                axes
        ));

        // Crédit produit HT
        BigDecimal montantHt = cmd.totalHt();
        BigDecimal montantTva = cmd.totalTva() != null ? cmd.totalTva() : BigDecimal.ZERO;
        // Certains historiques peuvent avoir total_tva non alimenté ; on dérive depuis TTC-HT.
        if (montantTva.compareTo(BigDecimal.ZERO) <= 0 && cmd.totalTtc() != null && montantHt != null) {
            BigDecimal derived = cmd.totalTtc().subtract(montantHt).setScale(2, java.math.RoundingMode.HALF_UP);
            if (derived.compareTo(BigDecimal.ZERO) > 0) {
                montantTva = derived;
            }
        }
        lignes.add(LigneEcriture.credit(
                mapping.compteVentes(),
                "Prestation dialyse - " + cmd.numeroFacture(),
                montantHt,
                null,
                axes
        ));

        // Crédit TVA collectée si la facture porte une TVA > 0.
        // On se base sur le snapshot de la facture (totalTva) pour préserver l'historique,
        // même si aucune règle TVA active n'est configurée au moment du rejeu.
        if (montantTva.compareTo(BigDecimal.ZERO) > 0) {
            lignes.add(LigneEcriture.credit(
                    mapping.compteTVACollectee(),
                    "TVA collectée - " + cmd.numeroFacture(),
                    montantTva,
                    null,
                    List.of()
            ));
        }

        return new EcritureComptable(
                UUID.randomUUID(),
                cmd.centerId(),
                JournalCode.VE,
                cmd.dateFacture(),
                cmd.dateFacture(),
                numeroPiece,
                "Fact. " + cmd.numeroFacture() + " - " + cmd.libelle(),
                lignes,
                StatutEcriture.VALIDEE,
                cmd.factureId()
        );
    }

    /**
     * Génère l'écriture journal banque/caisse pour un règlement encaissé.
     * <p>
     * Structure : débit 512/530 (trésorerie) / crédit 411xxx (apurement créance).
     */
    public EcritureComptable genererEcritureReglement(
            GenererEcritureReglementCommand cmd,
            MappingComptable mapping,
            String numeroPiece) {

        TypeTiersPayeur typeTiers = resoudreTypeTiers(cmd.typeTiersPayeur());
        String compteClient = mapping.compteClient(typeTiers);
        JournalCode journal = "CAISSE".equalsIgnoreCase(cmd.modeReglement()) ? JournalCode.CA : JournalCode.BQ;
        String compteTreso = journal == JournalCode.CA ? mapping.compteCaisse() : mapping.compteBanque();

        List<AxeAnalytique> axes = List.of(
                AxeAnalytique.centre(cmd.centerId().toString()),
                AxeAnalytique.typeTiers(typeTiers)
        );

        List<LigneEcriture> lignes = List.of(
                // Débit trésorerie
                LigneEcriture.debit(
                        compteTreso,
                        "Règlement " + cmd.tiersLibelle(),
                        cmd.montant().abs(),
                        null,
                        axes
                ),
                // Crédit apurement créance tiers
                LigneEcriture.credit(
                        compteClient,
                        "Règlement facture " + cmd.factureId(),
                        cmd.montant().abs(),
                        cmd.tiersPayeurId(),
                        axes
                )
        );

        return new EcritureComptable(
                UUID.randomUUID(),
                cmd.centerId(),
                journal,
                cmd.dateReglement(),
                cmd.dateReglement(),
                numeroPiece,
                "Règlement " + cmd.tiersLibelle(),
                lignes,
                StatutEcriture.VALIDEE,
                cmd.paiementId()
        );
    }

    // ─── Utilitaire ─────────────────────────────────────────────────────────

    private TypeTiersPayeur resoudreTypeTiers(String typeTiersStr) {
        if (typeTiersStr == null) return TypeTiersPayeur.AUTRE;
        try {
            return TypeTiersPayeur.valueOf(typeTiersStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TypeTiersPayeur.AUTRE;
        }
    }
}



