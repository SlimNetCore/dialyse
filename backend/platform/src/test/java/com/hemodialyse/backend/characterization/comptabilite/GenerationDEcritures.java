package com.hemodialyse.backend.characterization.comptabilite;

import com.hemodialyse.backend.contexts.comptabilite.application.ecriture.CloturerUnePeriode;
import com.hemodialyse.backend.contexts.comptabilite.application.vente.ComptabiliserLesVentesEtReglements;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.vente.FactureAComptabiliser;
import com.hemodialyse.backend.contexts.comptabilite.domain.vente.ReglementAComptabiliser;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Outil de test (slice C7) : génère les écritures de ventes et de règlements par le contexte {@code comptabilite}, comme le
 * faisait l'ancien port {@code ComptabiliteUseCase} retiré avec {@code bc-comptabilite}. Les suites de caractérisation C1
 * à C6 gardent ainsi leurs scénarios et leurs goldens ; un refus du contexte est rendu comme avant, en
 * {@link BusinessException} (même code, même message).
 */
@Component
public class GenerationDEcritures {

    private final ComptabiliserLesVentesEtReglements ventesEtReglements;
    private final CloturerUnePeriode cloture;

    public GenerationDEcritures(ComptabiliserLesVentesEtReglements ventesEtReglements, CloturerUnePeriode cloture) {
        this.ventesEtReglements = ventesEtReglements;
        this.cloture = cloture;
    }

    private static <T> T traduireLesRefus(Supplier<T> action) {
        try {
            return action.get();
        } catch (RegleMetierViolee refus) {
            throw new BusinessException(refus.code(), refus.getMessage());
        }
    }

    /**
     * Écriture de ventes d'une facture, datée de {@code dateFacture} (comme l'ancien port).
     */
    public EcritureComptable genererEcritureFacturation(Facture f) {
        return traduireLesRefus(() -> ventesEtReglements.comptabiliserUneVente(new CentreId(f.centerId()),
                new FactureAComptabiliser(f.factureId(), f.numeroFacture(), f.tiersPayeurId(), f.totalHt(), f.totalTva(),
                        f.totalTtc(), f.dateFacture(), f.dateFacture(), f.libelle()), f.dateFacture()));
    }

    public EcritureComptable genererEcritureReglement(Reglement r) {
        return traduireLesRefus(() -> ventesEtReglements.comptabiliserUnReglement(new CentreId(r.centerId()),
                new ReglementAComptabiliser(r.paiementId(), r.factureId(), r.montant(), r.dateReglement(),
                        r.modeReglement(), r.tiersLibelle(), r.tiersPayeurId())));
    }

    public void cloturerPeriode(UUID centre, YearMonth mois, String auteur) {
        traduireLesRefus(() -> {
            cloture.executer(new CentreId(centre), mois, auteur);
            return null;
        });
    }

    /**
     * Même forme que l'ancienne {@code GenererEcritureFacturationCommand}.
     */
    public record Facture(UUID centerId, UUID factureId, String numeroFacture, UUID patientId, UUID tiersPayeurId,
                          BigDecimal totalHt, BigDecimal totalTva, BigDecimal totalTtc, LocalDate dateFacture,
                          String libelle) {
    }

    /**
     * Même forme que l'ancienne {@code GenererEcritureReglementCommand}.
     */
    public record Reglement(UUID centerId, UUID factureId, UUID paiementId, BigDecimal montant, LocalDate dateReglement,
                            String modeReglement, String tiersLibelle, UUID tiersPayeurId) {
    }
}

