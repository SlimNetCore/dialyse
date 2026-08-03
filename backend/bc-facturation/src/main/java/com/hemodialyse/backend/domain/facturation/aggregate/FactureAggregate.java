package com.hemodialyse.backend.domain.facturation.aggregate;

import com.hemodialyse.backend.domain.facturation.entity.LigneFacture;
import com.hemodialyse.backend.domain.facturation.valueobject.FacturationPeriod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FactureAggregate {
    private final UUID id;
    private final UUID centerId;
    private final UUID patientId;
    private final String numeroFacture;
    private final String patientCode;
    private final String patientFullName;
    private final String patientStatusSnapshot;
    private final String numeroImmatriculationSnapshot;
    private final UUID centrePayeurIdSnapshot;
    private final UUID agenceIdSnapshot;
    private final FacturationPeriod period;
    private final LocalDate dateFacturation;
    private final BigDecimal tvaRate;
    private final List<LigneFacture> lignes;
    private final BigDecimal totalHt;
    private final BigDecimal totalTva;
    private final BigDecimal totalTtc;

    public FactureAggregate(UUID id,
                            UUID centerId,
                            UUID patientId,
                            String numeroFacture,
                            String patientCode,
                            String patientFullName,
                            String patientStatusSnapshot,
                            String numeroImmatriculationSnapshot,
                            UUID centrePayeurIdSnapshot,
                            UUID agenceIdSnapshot,
                            FacturationPeriod period,
                            LocalDate dateFacturation,
                            BigDecimal tvaRate,
                            List<LigneFacture> lignes) {
        if (id == null || centerId == null || patientId == null) {
            throw new IllegalArgumentException("Les identifiants facture, centre et patient sont obligatoires");
        }
        if (numeroFacture == null || numeroFacture.isBlank()) {
            throw new IllegalArgumentException("Le numero de facture est obligatoire");
        }
        if (period == null || dateFacturation == null || tvaRate == null) {
            throw new IllegalArgumentException("La periode, la date de facturation et la TVA sont obligatoires");
        }
        if (lignes == null || lignes.isEmpty()) {
            throw new IllegalArgumentException("Une facture doit contenir au moins une ligne");
        }
        this.id = id;
        this.centerId = centerId;
        this.patientId = patientId;
        this.numeroFacture = numeroFacture;
        this.patientCode = patientCode;
        this.patientFullName = patientFullName;
        this.patientStatusSnapshot = patientStatusSnapshot;
        this.numeroImmatriculationSnapshot = numeroImmatriculationSnapshot;
        this.centrePayeurIdSnapshot = centrePayeurIdSnapshot;
        this.agenceIdSnapshot = agenceIdSnapshot;
        this.period = period;
        this.dateFacturation = dateFacturation;
        this.tvaRate = tvaRate.setScale(2, RoundingMode.HALF_UP);
        this.lignes = List.copyOf(new ArrayList<>(lignes));
        this.totalHt = this.lignes.stream().map(LigneFacture::getLineHt)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        this.totalTva = this.totalHt.multiply(this.tvaRate.movePointLeft(2))
                .setScale(2, RoundingMode.HALF_UP);
        this.totalTtc = this.totalHt.add(this.totalTva).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public String getNumeroFacture() {
        return numeroFacture;
    }

    public String getPatientCode() {
        return patientCode;
    }

    public String getPatientFullName() {
        return patientFullName;
    }

    public String getPatientStatusSnapshot() {
        return patientStatusSnapshot;
    }

    public String getNumeroImmatriculationSnapshot() {
        return numeroImmatriculationSnapshot;
    }

    public UUID getCentrePayeurIdSnapshot() {
        return centrePayeurIdSnapshot;
    }

    public UUID getAgenceIdSnapshot() {
        return agenceIdSnapshot;
    }

    public FacturationPeriod getPeriod() {
        return period;
    }

    public LocalDate getDateFacturation() {
        return dateFacturation;
    }

    public BigDecimal getTvaRate() {
        return tvaRate;
    }

    public List<LigneFacture> getLignes() {
        return lignes;
    }

    public BigDecimal getTotalHt() {
        return totalHt;
    }

    public BigDecimal getTotalTva() {
        return totalTva;
    }

    public BigDecimal getTotalTtc() {
        return totalTtc;
    }
}

