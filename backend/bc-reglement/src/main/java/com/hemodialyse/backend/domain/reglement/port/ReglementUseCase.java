package com.hemodialyse.backend.domain.reglement.port;

import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public interface ReglementUseCase {

    PagedResult<ReglementFactureListItem> search(ReglementSearchQuery query);

    ReglementDashboardResult dashboard(ReglementDashboardQuery query);

    ReglementFactureListItem registerPayment(RegisterFacturePaymentCommand command);

    List<FacturePaymentItem> getPaymentHistory(GetPaymentHistoryQuery query);

    List<ReglementFactureListItem> exportList(ReglementSearchQuery query);

    enum FactureReglementEtat {
        NON_REGLEE,
        PARTIELLEMENT_REGLEE,
        REGLEE
    }

    enum FactureSoldeType {
        RESTE,
        REGLE,
        TROP_PERCU
    }

    record ReglementSearchQuery(
            CenterId centerId,
            int year,
            Integer month,
            UUID caisseId,
            UUID agenceId,
            UUID centrePayeurId,
            int page,
            int size
    ) {
        public ReglementSearchQuery {
            if (centerId == null) {
                throw new IllegalArgumentException("Le centerId est obligatoire");
            }
            if (year < 2000 || year > 3000) {
                throw new IllegalArgumentException("L'annee doit etre comprise entre 2000 et 3000");
            }
            if (month != null && (month < 1 || month > 12)) {
                throw new IllegalArgumentException("Le mois doit etre compris entre 1 et 12");
            }
            if (page < 0) {
                throw new IllegalArgumentException("La page doit etre positive");
            }
            if (size <= 0) {
                throw new IllegalArgumentException("La taille de page doit etre strictement positive");
            }
        }

        public LocalDate periodStart() {
            return YearMonth.of(year, month != null ? month : 1).atDay(1);
        }

        public LocalDate periodEnd() {
            return month != null
                    ? YearMonth.of(year, month).atEndOfMonth()
                    : LocalDate.of(year, 12, 31);
        }
    }

    record RegisterFacturePaymentCommand(
            CenterId centerId,
            UUID factureId,
            BigDecimal montant,
            LocalDate dateReglement,
            String userId
    ) {
        public RegisterFacturePaymentCommand {
            if (centerId == null || factureId == null) {
                throw new IllegalArgumentException("Le centerId et l'identifiant facture sont obligatoires");
            }
            if (montant == null || montant.signum() <= 0) {
                throw new IllegalArgumentException("Le montant du reglement doit etre strictement positif");
            }
        }

        public LocalDate effectiveDate() {
            return dateReglement != null ? dateReglement : LocalDate.now();
        }

        public String effectiveUserId() {
            return userId != null && !userId.isBlank() ? userId.trim() : "system";
        }
    }

    record ReglementDashboardQuery(CenterId centerId,
                                   int year,
                                   Integer month,
                                   UUID caisseId,
                                   UUID agenceId,
                                   UUID centrePayeurId
    ) {
        public ReglementDashboardQuery {
            if (centerId == null) {
                throw new IllegalArgumentException("Le centerId est obligatoire");
            }
            if (year < 2000 || year > 3000) {
                throw new IllegalArgumentException("L'annee doit etre comprise entre 2000 et 3000");
            }
            if (month != null && (month < 1 || month > 12)) {
                throw new IllegalArgumentException("Le mois doit etre compris entre 1 et 12");
            }
        }

        public LocalDate periodStart() {
            return YearMonth.of(year, month != null ? month : 1).atDay(1);
        }

        public LocalDate periodEnd() {
            return month != null
                    ? YearMonth.of(year, month).atEndOfMonth()
                    : LocalDate.of(year, 12, 31);
        }
    }

    record ReglementFactureListItem(
            UUID factureId,
            String numeroFacture,
            String numeroAssurance,
            String patientNom,
            String patientPrenom,
            UUID caisseId,
            String caisse,
            UUID agenceId,
            String agence,
            UUID centrePayeurId,
            String centrePayeur,
            LocalDate dateFacturation,
            BigDecimal montantFacture,
            BigDecimal montantRegle,
            BigDecimal reste,
            BigDecimal tropPercu,
            FactureReglementEtat etat,
            FactureSoldeType soldeType
    ) {
    }

    record ReglementDashboardBucket(
            String code,
            String label,
            long count,
            BigDecimal amount
    ) {
    }

    record ReglementDashboardResult(
            UUID centerId,
            int year,
            Integer month,
            long totalFactures,
            long nonReglees,
            long partiellementReglees,
            long reglees,
            long tropPercus,
            BigDecimal totalFacture,
            BigDecimal totalRegle,
            BigDecimal totalReste,
            BigDecimal totalTropPercu,
            List<ReglementDashboardBucket> statusBreakdown
    ) {
    }

    record GetPaymentHistoryQuery(
            CenterId centerId,
            UUID factureId
    ) {
        public GetPaymentHistoryQuery {
            if (centerId == null || factureId == null) {
                throw new IllegalArgumentException("Le centerId et l'identifiant facture sont obligatoires");
            }
        }
    }

    record FacturePaymentItem(
            UUID id,
            UUID factureId,
            BigDecimal montant,
            LocalDate dateReglement,
            String saisiPar
    ) {
    }
}




