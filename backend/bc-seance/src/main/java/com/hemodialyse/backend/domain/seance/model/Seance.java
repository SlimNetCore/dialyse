package com.hemodialyse.backend.domain.seance.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public class Seance {
    private UUID id;
    private UUID patientId;
    private UUID centerId;
    private LocalDate dateSeance;
    private SeanceStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime validatedAt;
    private OffsetDateTime signedByInfirmierAt;
    private String signedByInfirmierUserId;
    private OffsetDateTime signedByMedecinAt;
    private String signedByMedecinUserId;
    private UUID forfaitOverrideId;
    private String forfaitOverrideCode;
    private String forfaitOverrideNom;
    private BigDecimal forfaitOverridePrix;
    private OffsetDateTime forfaitOverrideUpdatedAt;
    private String forfaitOverrideUpdatedBy;
    /**
     * Déverrouillage pour régularisation : l'administrateur autorise l'infirmier à valider cette séance d'un jour passé.
     */
    private OffsetDateTime regularisationDeverrouilleeAt;
    private String regularisationDeverrouilleeBy;
    /**
     * Séance confirmée alors que le patient n'était pas programmé ce jour-là : motif déclaré par l'infirmier.
     */
    private boolean horsPlanning;
    private MotifHorsPlanning motifHorsPlanning;
    private String precisionHorsPlanning;

    public Seance() {
    }

    public Seance(UUID id, UUID patientId, UUID centerId, LocalDate dateSeance) {
        this.id = id;
        this.patientId = patientId;
        this.centerId = centerId;
        this.dateSeance = dateSeance;
        this.status = SeanceStatus.CREE;
        this.createdAt = OffsetDateTime.now();
    }

    /**
     * Marque la séance comme confirmée hors planning, avec le motif déclaré.
     */
    public void marquerHorsPlanning(DerogationPlanning derogation) {
        this.horsPlanning = true;
        this.motifHorsPlanning = derogation.motif();
        this.precisionHorsPlanning = derogation.precision();
    }

    public boolean isHorsPlanning() {
        return horsPlanning;
    }

    public void setHorsPlanning(boolean horsPlanning) {
        this.horsPlanning = horsPlanning;
    }

    public MotifHorsPlanning getMotifHorsPlanning() {
        return motifHorsPlanning;
    }

    public void setMotifHorsPlanning(MotifHorsPlanning motifHorsPlanning) {
        this.motifHorsPlanning = motifHorsPlanning;
    }

    public String getPrecisionHorsPlanning() {
        return precisionHorsPlanning;
    }

    public void setPrecisionHorsPlanning(String precisionHorsPlanning) {
        this.precisionHorsPlanning = precisionHorsPlanning;
    }

    public void validerParInfirmier(String userId) {
        if (status == SeanceStatus.FACTUREE) {
            throw new IllegalStateException("La seance facturee ne peut plus etre modifiee");
        }
        if (status == SeanceStatus.VALIDEE || status == SeanceStatus.SIGNEE) {
            return;
        }
        if (status != SeanceStatus.CREE) {
            throw new IllegalStateException("La seance n'est pas en statut CREE");
        }
        this.status = SeanceStatus.VALIDEE;
        if (this.dateSeance == null) {
            this.dateSeance = LocalDate.now();
        }
        this.validatedAt = OffsetDateTime.now();
        this.signedByInfirmierAt = this.validatedAt;
        this.signedByInfirmierUserId = userId;
    }

    /**
     * L'administrateur déverrouille une séance d'un jour passé restée « créée » (validation oubliée) : l'infirmier peut
     * alors la valider. Sans effet si elle l'est déjà ; refusé pour une séance du jour ou à venir (l'infirmier la valide
     * librement) ou qui n'est plus « créée ».
     */
    public void deverrouillerPourRegularisation(String userId, LocalDate aujourdhui) {
        if (status != SeanceStatus.CREE) {
            throw new BusinessException("SEANCE_DEVERROUILLAGE_INVALIDE",
                    "Seule une séance restée « créée » peut être déverrouillée pour régularisation");
        }
        if (dateSeance == null || !dateSeance.isBefore(aujourdhui)) {
            throw new BusinessException("SEANCE_DEVERROUILLAGE_INVALIDE",
                    "Seule une séance d'un jour passé peut être déverrouillée pour régularisation");
        }
        if (regularisationDeverrouilleeAt != null) {
            return;
        }
        this.regularisationDeverrouilleeAt = OffsetDateTime.now();
        this.regularisationDeverrouilleeBy = userId;
    }

    public boolean estDeverrouilleePourRegularisation() {
        return regularisationDeverrouilleeAt != null;
    }

    public OffsetDateTime getRegularisationDeverrouilleeAt() {
        return regularisationDeverrouilleeAt;
    }

    public void setRegularisationDeverrouilleeAt(OffsetDateTime regularisationDeverrouilleeAt) {
        this.regularisationDeverrouilleeAt = regularisationDeverrouilleeAt;
    }

    public String getRegularisationDeverrouilleeBy() {
        return regularisationDeverrouilleeBy;
    }

    public void setRegularisationDeverrouilleeBy(String regularisationDeverrouilleeBy) {
        this.regularisationDeverrouilleeBy = regularisationDeverrouilleeBy;
    }

    public void signerParMedecin(String userId) {
        if (status != SeanceStatus.VALIDEE) {
            throw new IllegalStateException("La signature medecin est autorisee uniquement apres validation infirmiere");
        }
        if (this.signedByMedecinAt != null) {
            throw new IllegalStateException("La seance est deja signee par un medecin");
        }
        this.signedByMedecinAt = OffsetDateTime.now();
        this.signedByMedecinUserId = userId;
        this.status = SeanceStatus.SIGNEE;
    }

    public void modifierForfait(UUID forfaitId, String forfaitCode, String forfaitNom, BigDecimal forfaitPrix, String userId) {
        if (status == SeanceStatus.FACTUREE) {
            throw new IllegalStateException("La seance facturee ne peut plus etre modifiee");
        }
        if (forfaitId == null) {
            throw new IllegalArgumentException("Le forfait est obligatoire");
        }
        this.forfaitOverrideId = forfaitId;
        this.forfaitOverrideCode = forfaitCode;
        this.forfaitOverrideNom = forfaitNom;
        this.forfaitOverridePrix = forfaitPrix;
        this.forfaitOverrideUpdatedAt = OffsetDateTime.now();
        this.forfaitOverrideUpdatedBy = userId == null || userId.isBlank() ? "system" : userId.trim();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public void setPatientId(UUID patientId) {
        this.patientId = patientId;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public void setCenterId(UUID centerId) {
        this.centerId = centerId;
    }

    public LocalDate getDateSeance() {
        return dateSeance;
    }

    public void setDateSeance(LocalDate dateSeance) {
        this.dateSeance = dateSeance;
    }

    public SeanceStatus getStatus() {
        return status;
    }

    public void setStatus(SeanceStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(OffsetDateTime validatedAt) {
        this.validatedAt = validatedAt;
    }

    public OffsetDateTime getSignedByInfirmierAt() {
        return signedByInfirmierAt;
    }

    public void setSignedByInfirmierAt(OffsetDateTime signedByInfirmierAt) {
        this.signedByInfirmierAt = signedByInfirmierAt;
    }

    public String getSignedByInfirmierUserId() {
        return signedByInfirmierUserId;
    }

    public void setSignedByInfirmierUserId(String signedByInfirmierUserId) {
        this.signedByInfirmierUserId = signedByInfirmierUserId;
    }

    public OffsetDateTime getSignedByMedecinAt() {
        return signedByMedecinAt;
    }

    public void setSignedByMedecinAt(OffsetDateTime signedByMedecinAt) {
        this.signedByMedecinAt = signedByMedecinAt;
    }

    public String getSignedByMedecinUserId() {
        return signedByMedecinUserId;
    }

    public void setSignedByMedecinUserId(String signedByMedecinUserId) {
        this.signedByMedecinUserId = signedByMedecinUserId;
    }

    public UUID getForfaitOverrideId() {
        return forfaitOverrideId;
    }

    public void setForfaitOverrideId(UUID forfaitOverrideId) {
        this.forfaitOverrideId = forfaitOverrideId;
    }

    public String getForfaitOverrideCode() {
        return forfaitOverrideCode;
    }

    public void setForfaitOverrideCode(String forfaitOverrideCode) {
        this.forfaitOverrideCode = forfaitOverrideCode;
    }

    public String getForfaitOverrideNom() {
        return forfaitOverrideNom;
    }

    public void setForfaitOverrideNom(String forfaitOverrideNom) {
        this.forfaitOverrideNom = forfaitOverrideNom;
    }

    public BigDecimal getForfaitOverridePrix() {
        return forfaitOverridePrix;
    }

    public void setForfaitOverridePrix(BigDecimal forfaitOverridePrix) {
        this.forfaitOverridePrix = forfaitOverridePrix;
    }

    public OffsetDateTime getForfaitOverrideUpdatedAt() {
        return forfaitOverrideUpdatedAt;
    }

    public void setForfaitOverrideUpdatedAt(OffsetDateTime forfaitOverrideUpdatedAt) {
        this.forfaitOverrideUpdatedAt = forfaitOverrideUpdatedAt;
    }

    public String getForfaitOverrideUpdatedBy() {
        return forfaitOverrideUpdatedBy;
    }

    public void setForfaitOverrideUpdatedBy(String forfaitOverrideUpdatedBy) {
        this.forfaitOverrideUpdatedBy = forfaitOverrideUpdatedBy;
    }
}


