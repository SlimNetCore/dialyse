package com.hemodialyse.backend.infrastructure.listener;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ComptabilisationDesVentesEtReglements;
import com.hemodialyse.backend.domain.facturation.event.FacturationValideeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Politique P1 (slice C5) : une validation de facturation produit une écriture de ventes par facture de la période,
 * par le contexte {@code comptabilite}. Traducteur de l'événement legacy vers le langage publié du contexte
 * ({@link ComptabilisationDesVentesEtReglements}) : aucun import de l'intérieur du contexte.
 * <p>
 * Décision KB-CPT-37 (2026-10-19) : la comptabilisation a lieu <b>après le commit</b> de la facturation, chaque facture
 * dans sa propre transaction. Une facture refusée (mois clôturé, facture déséquilibrée...) ne fait jamais échouer la
 * facturation et n'empêche pas les suivantes ; elle est journalisée et reste à régénérer
 * ({@code GET /api/v1/comptabilite/ventes/non-comptabilisees}, puis {@code POST /api/v1/comptabilite/rebuild}).
 * Publié hors transaction, l'événement est traité tout de suite.
 */
@Component
public class FacturationValideeComptabiliteListener {

    private static final Logger log = LoggerFactory.getLogger(FacturationValideeComptabiliteListener.class);

    private final ComptabilisationDesVentesEtReglements comptabilisation;

    public FacturationValideeComptabiliteListener(ComptabilisationDesVentesEtReglements comptabilisation) {
        this.comptabilisation = comptabilisation;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onFacturationValidee(FacturationValideeEvent event) {
        try {
            comptabilisation.facturationValidee(event.centerId(), event.periodStart(), event.periodEnd())
                    .forEach(echec -> log.warn(
                    "Écriture de ventes non générée pour la facture {} ({}) : {} {}", echec.reference(), echec.source(),
                    echec.code(), echec.message()));
        } catch (RuntimeException ex) {
            log.error("Erreur inattendue pendant la comptabilisation des ventes du centre {}", event.centerId(), ex);
        }
    }
}

