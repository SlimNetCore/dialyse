package com.hemodialyse.backend.contexts.comptabilite.application.tva;

import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTva;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ajoute une règle de TVA au centre. Refusée (400) si sa fin précède son début ou si elle chevauche une règle active du même
 * type (KB-CPT-16) ; les règles s'ajoutent seulement, jamais modifiées ni fermées. Deux ajouts simultanés ne sont pas
 * sérialisés : la limite est consignée dans la fiche de la slice.
 */
public final class EnregistrerUneRegleDeTva {

    private final RegleTvaRepository regles;
    private final UniteDeTravail uniteDeTravail;

    public EnregistrerUneRegleDeTva(RegleTvaRepository regles, UniteDeTravail uniteDeTravail) {
        this.regles = regles;
        this.uniteDeTravail = uniteDeTravail;
    }

    public void executer(CentreId centre, Commande commande) {
        RegleTva nouvelle = RegleTva.definir(centre, commande.typePrestation(), commande.tauxApplique(), commande.exonere(),
                commande.dateDebutValidite(), commande.dateFinValidite(), commande.texteReference());
        uniteDeTravail.executer(() -> {
            nouvelle.exigerSansChevauchement(regles.duCentre(centre));
            regles.ajouter(nouvelle);
            return null;
        });
    }

    public record Commande(String typePrestation, BigDecimal tauxApplique, boolean exonere, LocalDate dateDebutValidite,
                           LocalDate dateFinValidite, String texteReference) {
    }
}
