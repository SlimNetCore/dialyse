package com.hemodialyse.backend.domain.stock.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.Money;
import com.hemodialyse.backend.domain.shared.vo.Quantite;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A lot (batch) of an article received through a bon de reception.
 * Carries the lot number, expiry date, remaining quantity and the PMP
 * (poids moyen pondere / weighted average cost) computed at reception time.
 */
public class Lot {
    private UUID id;
    private UUID centerId;
    private UUID articleId;
    private UUID bonReceptionId;
    private UUID emplacementId;
    private String numeroLot;
    private LocalDate datePeremption;
    private BigDecimal quantiteInitiale;
    private BigDecimal quantiteRestante;
    private BigDecimal pmp;
    private OffsetDateTime createdAt;

    public Lot() {
    }

    public static Lot create(UUID centerId, UUID articleId, UUID bonReceptionId, UUID emplacementId,
                             String numeroLot, LocalDate datePeremption,
                             BigDecimal quantite, BigDecimal pmp) {
        // Aggregate invariants (Shared Kernel): valid quantity and non-negative PMP.
        Quantite.of(quantite);
        if (pmp != null) {
            Money.of(pmp);
        }
        Lot lot = new Lot();
        lot.id = UUID.randomUUID();
        lot.centerId = centerId;
        lot.articleId = articleId;
        lot.bonReceptionId = bonReceptionId;
        lot.emplacementId = emplacementId;
        lot.numeroLot = numeroLot;
        lot.datePeremption = datePeremption;
        lot.quantiteInitiale = quantite;
        lot.quantiteRestante = quantite;
        lot.pmp = pmp;
        lot.createdAt = OffsetDateTime.now();
        return lot;
    }

    /**
     * Consume a quantity from this lot (used by FEFO exits).
     */
    public void consommer(BigDecimal quantite) {
        Quantite aConsommer = Quantite.of(quantite);
        if (!aConsommer.isPositive()) {
            throw new BusinessException("La quantite a consommer doit etre strictement positive");
        }
        Quantite restante = Quantite.of(quantiteRestante != null ? quantiteRestante : BigDecimal.ZERO);
        if (restante.value().compareTo(aConsommer.value()) < 0) {
            throw new BusinessException("Quantite insuffisante sur le lot " + numeroLot);
        }
        this.quantiteRestante = restante.value().subtract(aConsommer.value());
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public void setCenterId(UUID centerId) {
        this.centerId = centerId;
    }

    public UUID getArticleId() {
        return articleId;
    }

    public void setArticleId(UUID articleId) {
        this.articleId = articleId;
    }

    public UUID getBonReceptionId() {
        return bonReceptionId;
    }

    public void setBonReceptionId(UUID bonReceptionId) {
        this.bonReceptionId = bonReceptionId;
    }

    public UUID getEmplacementId() {
        return emplacementId;
    }

    public void setEmplacementId(UUID emplacementId) {
        this.emplacementId = emplacementId;
    }

    public String getNumeroLot() {
        return numeroLot;
    }

    public void setNumeroLot(String numeroLot) {
        this.numeroLot = numeroLot;
    }

    public LocalDate getDatePeremption() {
        return datePeremption;
    }

    public void setDatePeremption(LocalDate datePeremption) {
        this.datePeremption = datePeremption;
    }

    public BigDecimal getQuantiteInitiale() {
        return quantiteInitiale;
    }

    public void setQuantiteInitiale(BigDecimal quantiteInitiale) {
        this.quantiteInitiale = quantiteInitiale;
    }

    public BigDecimal getQuantiteRestante() {
        return quantiteRestante;
    }

    public void setQuantiteRestante(BigDecimal quantiteRestante) {
        this.quantiteRestante = quantiteRestante;
    }

    public BigDecimal getPmp() {
        return pmp;
    }

    public void setPmp(BigDecimal pmp) {
        this.pmp = pmp;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

