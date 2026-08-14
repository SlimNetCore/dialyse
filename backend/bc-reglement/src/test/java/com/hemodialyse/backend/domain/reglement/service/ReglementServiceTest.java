package com.hemodialyse.backend.domain.reglement.service;

import com.hemodialyse.backend.domain.reglement.aggregate.FactureReglementAggregate;
import com.hemodialyse.backend.domain.reglement.entity.FacturePayment;
import com.hemodialyse.backend.domain.reglement.port.ReglementUseCase;
import com.hemodialyse.backend.domain.reglement.repository.ReglementRepository;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReglementServiceTest {

    @Test
    void registerPayment_should_compute_partial_then_settle_then_overpay() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID factureId = UUID.randomUUID();
        InMemoryRepository repository = new InMemoryRepository(centerId.value(), factureId, new BigDecimal("1000.00"));
        ReglementService service = new ReglementService(repository);

        ReglementUseCase.ReglementFactureListItem firstPayment = service.registerPayment(
                new ReglementUseCase.RegisterFacturePaymentCommand(centerId, factureId, new BigDecimal("400.00"), LocalDate.of(2026, 8, 14), "user-a")
        );
        assertEquals(ReglementUseCase.FactureReglementEtat.PARTIELLEMENT_REGLEE, firstPayment.etat());
        assertEquals(new BigDecimal("600.00"), firstPayment.reste());
        assertEquals(new BigDecimal("0.00"), firstPayment.tropPercu());

        ReglementUseCase.ReglementFactureListItem secondPayment = service.registerPayment(
                new ReglementUseCase.RegisterFacturePaymentCommand(centerId, factureId, new BigDecimal("600.00"), LocalDate.of(2026, 8, 15), "user-b")
        );
        assertEquals(ReglementUseCase.FactureReglementEtat.REGLEE, secondPayment.etat());
        assertEquals(ReglementUseCase.FactureSoldeType.REGLE, secondPayment.soldeType());
        assertEquals(new BigDecimal("0.00"), secondPayment.reste());

        ReglementUseCase.ReglementFactureListItem thirdPayment = service.registerPayment(
                new ReglementUseCase.RegisterFacturePaymentCommand(centerId, factureId, new BigDecimal("50.00"), LocalDate.of(2026, 8, 16), "user-c")
        );
        assertEquals(ReglementUseCase.FactureReglementEtat.REGLEE, thirdPayment.etat());
        assertEquals(ReglementUseCase.FactureSoldeType.TROP_PERCU, thirdPayment.soldeType());
        assertEquals(new BigDecimal("50.00"), thirdPayment.tropPercu());
        assertNotNull(repository.lastSavedPayment);
        assertEquals("user-c", repository.lastSavedPayment.saisiPar());
    }

    @Test
    void registerPayment_should_allow_negative_to_correct_trop_percu() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID factureId = UUID.randomUUID();
        InMemoryRepository repository = new InMemoryRepository(centerId.value(), factureId, new BigDecimal("1000.00"));
        ReglementService service = new ReglementService(repository);

        // Payer en trop
        service.registerPayment(new ReglementUseCase.RegisterFacturePaymentCommand(
                centerId, factureId, new BigDecimal("1200.00"), LocalDate.of(2026, 8, 14), "user-a"));

        // Corriger le trop-perçu avec un montant négatif
        ReglementUseCase.ReglementFactureListItem corrected = service.registerPayment(
                new ReglementUseCase.RegisterFacturePaymentCommand(
                        centerId, factureId, new BigDecimal("-200.00"), LocalDate.of(2026, 8, 15), "user-b"));

        assertEquals(ReglementUseCase.FactureReglementEtat.REGLEE, corrected.etat());
        assertEquals(ReglementUseCase.FactureSoldeType.REGLE, corrected.soldeType());
        assertEquals(new BigDecimal("0.00"), corrected.tropPercu());
        assertEquals(new BigDecimal("0.00"), corrected.reste());
    }

    private static final class InMemoryRepository implements ReglementRepository {
        private final UUID centerId;
        private final UUID factureId;
        private final BigDecimal montantFacture;
        private final List<FacturePayment> payments = new ArrayList<>();
        private FacturePayment lastSavedPayment;

        private InMemoryRepository(UUID centerId, UUID factureId, BigDecimal montantFacture) {
            this.centerId = centerId;
            this.factureId = factureId;
            this.montantFacture = montantFacture;
        }

        @Override
        public PagedResult<ReglementUseCase.ReglementFactureListItem> search(ReglementUseCase.ReglementSearchQuery query) {
            return PagedResult.of(List.of(getFactureItem(query.centerId(), factureId)), 1, query.page(), query.size());
        }

        @Override
        public ReglementUseCase.ReglementDashboardResult dashboard(ReglementUseCase.ReglementDashboardQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FactureReglementAggregate loadAggregate(CenterId centerId, UUID factureId) {
            return new FactureReglementAggregate(factureId, centerId.value(), montantFacture, payments);
        }

        @Override
        public void savePayment(FacturePayment payment) {
            payments.add(payment);
            lastSavedPayment = payment;
        }

        @Override
        public ReglementUseCase.ReglementFactureListItem getFactureItem(CenterId centerId, UUID factureId) {
            BigDecimal amountPaid = payments.stream().map(FacturePayment::montant).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2);
            BigDecimal reste = montantFacture.subtract(amountPaid);
            BigDecimal tropPercu = BigDecimal.ZERO;
            ReglementUseCase.FactureReglementEtat etat;
            ReglementUseCase.FactureSoldeType soldeType;
            if (amountPaid.signum() == 0) {
                etat = ReglementUseCase.FactureReglementEtat.NON_REGLEE;
                soldeType = ReglementUseCase.FactureSoldeType.RESTE;
            } else if (amountPaid.compareTo(montantFacture) < 0) {
                etat = ReglementUseCase.FactureReglementEtat.PARTIELLEMENT_REGLEE;
                soldeType = ReglementUseCase.FactureSoldeType.RESTE;
            } else if (amountPaid.compareTo(montantFacture) > 0) {
                etat = ReglementUseCase.FactureReglementEtat.REGLEE;
                soldeType = ReglementUseCase.FactureSoldeType.TROP_PERCU;
                tropPercu = amountPaid.subtract(montantFacture).setScale(2);
                reste = BigDecimal.ZERO.setScale(2);
            } else {
                etat = ReglementUseCase.FactureReglementEtat.REGLEE;
                soldeType = ReglementUseCase.FactureSoldeType.REGLE;
                reste = BigDecimal.ZERO.setScale(2);
            }
            return new ReglementUseCase.ReglementFactureListItem(
                    factureId,
                    "FAC-2026-0001",
                    "ASS-01",
                    "Patient",
                    "Test",
                    UUID.randomUUID(),
                    "CNAS",
                    UUID.randomUUID(),
                    "Agence A",
                    UUID.randomUUID(),
                    "Centre Payeur A",
                    LocalDate.of(2026, 8, 1),
                    montantFacture,
                    amountPaid,
                    reste.setScale(2),
                    tropPercu.setScale(2),
                    etat,
                    soldeType,
                    null,
                    payments.size()
            );
        }

        @Override
        public List<ReglementUseCase.FacturePaymentItem> getPaymentHistory(CenterId centerId, UUID factureId) {
            return payments.stream()
                    .map(payment -> new ReglementUseCase.FacturePaymentItem(
                            payment.id(),
                            payment.factureId(),
                            payment.montant(),
                            payment.dateReglement(),
                            payment.saisiPar(),
                            payment.codeReglement()
                    ))
                    .toList();
        }

        @Override
        public List<ReglementUseCase.ReglementFactureListItem> exportList(ReglementUseCase.ReglementSearchQuery query) {
            return List.of(getFactureItem(query.centerId(), this.factureId));
        }
    }
}




