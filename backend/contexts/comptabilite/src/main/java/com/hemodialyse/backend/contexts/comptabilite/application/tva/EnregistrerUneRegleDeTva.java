package com.hemodialyse.backend.contexts.comptabilite.application.tva;

import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTva;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ajoute une règle de TVA au centre, selon la règle unique de saisie (ADR 0030) : refusée (400) si son taux sort de 0 à
 * 100 %, si sa fin précède son début ou si elle chevauche une règle active du même type (KB-CPT-16, KB-CPT-17). Deux ajouts
 * simultanés ne sont pas sérialisés : la limite est consignée dans la fiche de la slice.
 */
public final class EnregistrerUneRegleDeTva {

    private final RegleTvaRepository regles;
    private final UniteDeTravail uniteDeTravail;

    public EnregistrerUneRegleDeTva(RegleTvaRepository regles, UniteDeTravail uniteDeTravail) {
        this.regles = regles;
        this.uniteDeTravail = uniteDeTravail;
    }

    public void executer(CentreId centre, Commande commande) {
        RegleTva nouvelle = RegleTva.definir(new RegleTvaId(UUID.randomUUID()), centre, commande.typePrestation(),
                commande.tauxApplique(), commande.exonere(), commande.dateDebutValidite(), commande.dateFinValidite(),
                commande.texteReference());
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
