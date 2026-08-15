package com.hemodialyse.backend.infrastructure.listener;

import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteUseCase;
import com.hemodialyse.backend.domain.reglement.event.FactureReglementRecordedEvent;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Listener Spring qui génère une écriture comptable lors de chaque règlement encaissé.
 * <p>
 * Idempotence : si l'écriture existe déjà (paiementId déjà traité), le service de domaine
 * retourne l'existante sans créer de doublon (voir ComptabiliteService).
 * <p>
 * Ce listener est déclenché après le commit de la transaction de règlement (@TransactionalEventListener
 * serait plus robuste en prod, mais @EventListener avec @Transactional est suffisant en dev/H2).
 */
@Component
public class ReglementEncaisseComptabiliteListener {

    private static final Logger log = LoggerFactory.getLogger(ReglementEncaisseComptabiliteListener.class);

    private final ComptabiliteUseCase comptabiliteUseCase;
    private final JdbcTemplate jdbcTemplate;

    public ReglementEncaisseComptabiliteListener(ComptabiliteUseCase comptabiliteUseCase,
                                                 JdbcTemplate jdbcTemplate) {
        this.comptabiliteUseCase = comptabiliteUseCase;
        this.jdbcTemplate = jdbcTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReglementEncaisse(FactureReglementRecordedEvent event) {
        try {
            UUID paiementId = resolvePaiementId(event);
            var cmd = new ComptabiliteUseCase.GenererEcritureReglementCommand(
                    event.centerId(),
                    event.factureId(),
                    paiementId,
                    event.montant(),
                    event.dateReglement(),
                    "BANQUE", // mode par défaut — à enrichir avec un champ dans l'event v2
                    "Règlement facture",
                    null, // tiersPayeurId — enrichir avec données facture si nécessaire
                    "AUTRE"
            );
            comptabiliteUseCase.genererEcritureReglement(cmd);
        } catch (BusinessException ex) {
            // Période clôturée : loguer sans bloquer le règlement
            log.warn("Génération écriture comptable ignorée pour règlement {} : {}",
                    event.factureId(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Erreur inattendue lors de la génération écriture comptable pour règlement {}",
                    event.factureId(), ex);
        }
    }

    private UUID resolvePaiementId(FactureReglementRecordedEvent event) {
        UUID found = jdbcTemplate.query(
                """
                        SELECT fr.id
                        FROM facture_reglements fr
                        WHERE fr.facture_id = ?
                          AND fr.center_id = ?
                          AND fr.date_reglement = ?
                          AND fr.montant = ?
                          AND COALESCE(fr.saisi_par, '') = COALESCE(?, '')
                        ORDER BY fr.created_at DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                event.factureId(), event.centerId(), event.dateReglement(), event.montant(), event.userId()
        ).stream().findFirst().orElse(null);
        return found != null ? found : UUID.nameUUIDFromBytes(
                (event.centerId() + "|" + event.factureId() + "|" + event.dateReglement() + "|" + event.montant() + "|" + event.userId()).getBytes()
        );
    }
}



