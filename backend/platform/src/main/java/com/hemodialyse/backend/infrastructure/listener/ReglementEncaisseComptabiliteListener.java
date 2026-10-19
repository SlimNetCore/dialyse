package com.hemodialyse.backend.infrastructure.listener;

import com.hemodialyse.backend.contexts.comptabilite.application.vente.ComptabiliserLesVentesEtReglements;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.domain.reglement.event.FactureReglementRecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Politique P2 (slice C5) : un règlement enregistré produit son écriture de banque, par le contexte {@code comptabilite}.
 * <p>
 * Décision KB-CPT-37 (2026-10-19) : la comptabilisation a lieu <b>après le commit</b> du règlement, dans sa propre
 * transaction. Un refus (mois clôturé...) ne fait jamais échouer l'enregistrement du règlement ; il est journalisé et le
 * règlement reste à régénérer ({@code POST /api/v1/comptabilite/rebuild}). Publié hors transaction, l'événement est traité
 * tout de suite.
 */
@Component
public class ReglementEncaisseComptabiliteListener {

    private static final Logger log = LoggerFactory.getLogger(ReglementEncaisseComptabiliteListener.class);

    private final ComptabiliserLesVentesEtReglements comptabilisation;

    public ReglementEncaisseComptabiliteListener(ComptabiliserLesVentesEtReglements comptabilisation) {
        this.comptabilisation = comptabilisation;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReglementEncaisse(FactureReglementRecordedEvent event) {
        try {
            comptabilisation.comptabiliserLeReglementEnregistre(new CentreId(event.centerId()), event.factureId(),
                    event.montant(), event.dateReglement(), event.userId()).echecs().forEach(echec -> log.warn(
                    "Écriture de règlement non générée pour la facture {} ({}) : {} {}", echec.reference(),
                    echec.source(), echec.code(), echec.message()));
        } catch (RuntimeException ex) {
            log.error("Erreur inattendue lors de la comptabilisation du règlement de la facture {}", event.factureId(), ex);
        }
    }
}
