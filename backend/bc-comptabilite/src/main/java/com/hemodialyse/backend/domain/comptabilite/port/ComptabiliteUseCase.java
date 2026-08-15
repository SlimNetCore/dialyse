package com.hemodialyse.backend.domain.comptabilite.port;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.MappingComptable;
import com.hemodialyse.backend.domain.comptabilite.valueobject.RegleTVA;
import com.hemodialyse.backend.domain.comptabilite.valueobject.StatutEcriture;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Port entrant (use case) du module comptabilité.
 * Les sous-interfaces/records imbriqués définissent les commandes et requêtes.
 */
public interface ComptabiliteUseCase {

    // ─── Génération d'écritures ─────────────────────────────────────────────

    EcritureComptable genererEcritureFacturation(GenererEcritureFacturationCommand cmd);

    EcritureComptable genererEcritureReglement(GenererEcritureReglementCommand cmd);

    // ─── Consultation ───────────────────────────────────────────────────────

    PagedResult<EcritureComptable> search(SearchEcrituresQuery query);

    // ─── Validation / Export ────────────────────────────────────────────────

    EcritureComptable valider(ValiderEcritureCommand cmd);

    byte[] exporter(ExporterJournalQuery query);

    // ─── Clôture de période ─────────────────────────────────────────────────

    void cloturerPeriode(CloturerPeriodeCommand cmd);

    // ─── Paramétrage ────────────────────────────────────────────────────────

    MappingComptable getMappingComptable(UUID centerId);

    MappingComptable saveMappingComptable(MappingComptable mapping);

    List<RegleTVA> getReglesTV(UUID centerId);

    void saveRegleTVA(UUID centerId, RegleTVA regle);

    // ═══ Commands / Queries ══════════════════════════════════════════════════

    record GenererEcritureFacturationCommand(
            UUID centerId,
            UUID factureId,
            String numeroFacture,
            UUID patientId,
            UUID tiersPayeurId,
            String typeTiersPayeur,
            BigDecimal totalHt,
            BigDecimal totalTva,
            BigDecimal totalTtc,
            LocalDate dateFacture,
            String libelle
    ) {
        public GenererEcritureFacturationCommand {
            if (centerId == null || factureId == null) {
                throw new IllegalArgumentException("centerId et factureId sont obligatoires");
            }
        }
    }

    record GenererEcritureReglementCommand(
            UUID centerId,
            UUID factureId,
            UUID paiementId,
            BigDecimal montant,
            LocalDate dateReglement,
            String modeReglement,
            String tiersLibelle,
            UUID tiersPayeurId,
            String typeTiersPayeur
    ) {
        public GenererEcritureReglementCommand {
            if (centerId == null || paiementId == null) {
                throw new IllegalArgumentException("centerId et paiementId sont obligatoires");
            }
        }
    }

    record SearchEcrituresQuery(
            UUID centerId,
            LocalDate from,
            LocalDate to,
            JournalCode journalCode,
            StatutEcriture statut,
            int page,
            int size
    ) {
    }

    record ValiderEcritureCommand(UUID centerId, UUID ecritureId) {
    }

    record ExporterJournalQuery(
            UUID centerId,
            LocalDate from,
            LocalDate to,
            JournalCode journalCode,
            String format
    ) {
    }

    record CloturerPeriodeCommand(UUID centerId, YearMonth periode, String userId) {
    }
}

