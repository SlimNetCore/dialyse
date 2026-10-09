package com.hemodialyse.backend.domain.comptabilite.service;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.entity.LigneEcriture;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteUseCase.GenererEcritureFacturationCommand;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteUseCase.GenererEcritureReglementCommand;
import com.hemodialyse.backend.domain.comptabilite.valueobject.AxeAnalytique;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.MappingComptable;
import com.hemodialyse.backend.domain.comptabilite.valueobject.OperationComptable;
import com.hemodialyse.backend.domain.comptabilite.valueobject.StatutEcriture;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service de génération pur — sans I/O, sans Spring, sans JPA.
 * Reçoit le mapping et le compte client résolus par l'appelant (aucune résolution ici) : le compte client est celui
 * du payeur de la facture, paramétré payeur par payeur.
 */
public class GenerateurEcritureComptable {

    /**
     * Génère l'écriture du journal des ventes pour une facture émise.
     * <p>
     * Structure : débit compte client du payeur / crédit 706 (produit prestation).
     * Si TVA active : crédit 44571 en plus du crédit 706.
     */
    public EcritureComptable genererEcritureFacturation(
            GenererEcritureFacturationCommand cmd,
            MappingComptable mapping,
            String compteClient,
            String numeroPiece) {

        List<LigneEcriture> lignes = new ArrayList<>();
        List<AxeAnalytique> axes = List.of(AxeAnalytique.centre(cmd.centerId().toString()));

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
        // On se base sur le snapshot de la facture (totalTva) pour préserver l'historique.
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
                mapping.journalDe(OperationComptable.VENTE),
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
     * Génère l'écriture du journal de banque ou de caisse pour un règlement encaissé.
     * <p>
     * Structure : débit 512/530 (trésorerie) / crédit compte client (apurement de la créance).
     */
    public EcritureComptable genererEcritureReglement(
            GenererEcritureReglementCommand cmd,
            MappingComptable mapping,
            String compteClient,
            String numeroPiece) {

        boolean caisse = "CAISSE".equalsIgnoreCase(cmd.modeReglement());
        JournalCode journal = mapping.journalDe(
                caisse ? OperationComptable.REGLEMENT_CAISSE : OperationComptable.REGLEMENT_BANQUE);
        String compteTreso = caisse ? mapping.compteCaisse() : mapping.compteBanque();

        List<AxeAnalytique> axes = List.of(AxeAnalytique.centre(cmd.centerId().toString()));

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
}
