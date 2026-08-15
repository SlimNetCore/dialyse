package com.hemodialyse.backend.infrastructure.listener;

import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteUseCase;
import com.hemodialyse.backend.domain.facturation.event.FacturationValideeEvent;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Génère les écritures VE après une validation de facturation.
 * <p>
 * L'événement métier actuel est global (pas par facture), donc ce listener recharge
 * les factures de la période concernée et laisse l'idempotence domaine éviter les doublons.
 * <p>
 * Le déclenchement est synchrone afin de garantir que les écritures sont visibles
 * immédiatement après la validation de facturation, y compris dans les scénarios
 * d'intégration/test et lors du retour direct vers l'écran comptabilité.
 */
@Component
public class FacturationValideeComptabiliteListener {

    private static final Logger log = LoggerFactory.getLogger(FacturationValideeComptabiliteListener.class);

    private final ComptabiliteUseCase comptabiliteUseCase;
    private final JdbcTemplate jdbcTemplate;

    public FacturationValideeComptabiliteListener(ComptabiliteUseCase comptabiliteUseCase,
                                                  JdbcTemplate jdbcTemplate) {
        this.comptabiliteUseCase = comptabiliteUseCase;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener
    public void onFacturationValidee(FacturationValideeEvent event) {
        try {
            LocalDate start = event.periodStart();
            LocalDate end = event.periodEnd();
            List<FactureRow> factures = jdbcTemplate.query(
                    """
                            SELECT f.id, f.center_id, f.numero_facture, f.patient_id,
                                   f.centre_payeur_id_snapshot, f.total_ht, f.total_tva, f.total_ttc,
                                   f.date_facturation, COALESCE(f.patient_full_name, 'Facture') AS libelle
                            FROM factures f
                            WHERE f.center_id = ?
                              AND f.date_facturation BETWEEN ? AND ?
                            ORDER BY f.date_facturation, f.numero_facture
                            """,
                    (rs, rowNum) -> new FactureRow(
                            rs.getObject("id", UUID.class),
                            rs.getObject("center_id", UUID.class),
                            rs.getString("numero_facture"),
                            rs.getObject("patient_id", UUID.class),
                            rs.getObject("centre_payeur_id_snapshot", UUID.class),
                            rs.getBigDecimal("total_ht"),
                            rs.getBigDecimal("total_tva"),
                            rs.getBigDecimal("total_ttc"),
                            rs.getObject("date_facturation", LocalDate.class),
                            rs.getString("libelle")
                    ),
                    event.centerId(), start, end
            );

            for (FactureRow f : factures) {
                comptabiliteUseCase.genererEcritureFacturation(new ComptabiliteUseCase.GenererEcritureFacturationCommand(
                        f.centerId,
                        f.factureId,
                        f.numeroFacture,
                        f.patientId,
                        f.centrePayeurId,
                        "AUTRE",
                        f.totalHt != null ? f.totalHt : BigDecimal.ZERO,
                        f.totalTva != null ? f.totalTva : BigDecimal.ZERO,
                        f.totalTtc != null ? f.totalTtc : BigDecimal.ZERO,
                        f.dateFacturation,
                        f.libelle
                ));
            }
        } catch (BusinessException ex) {
            log.warn("Génération écritures facturation ignorée: {}", ex.getMessage());
        } catch (Exception ex) {
            log.error("Erreur inattendue pendant la génération comptable facturation", ex);
        }
    }

    private record FactureRow(
            UUID factureId,
            UUID centerId,
            String numeroFacture,
            UUID patientId,
            UUID centrePayeurId,
            BigDecimal totalHt,
            BigDecimal totalTva,
            BigDecimal totalTtc,
            LocalDate dateFacturation,
            String libelle
    ) {
    }
}





