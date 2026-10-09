package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mapping_comptable",
        uniqueConstraints = @UniqueConstraint(name = "uq_mapping_center", columnNames = "center_id"))
public class MappingComptableJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "center_id", nullable = false, columnDefinition = "uuid")
    private UUID centerId;

    @Column(name = "compte_ventes", length = 20, nullable = false)
    private String compteVentes;

    @Column(name = "compte_client_patient", length = 20, nullable = false)
    private String compteClientPatient;

    @Column(name = "compte_client_cnas", length = 20, nullable = false)
    private String compteClientCnas;

    @Column(name = "compte_client_casnos", length = 20, nullable = false)
    private String compteClientCasnos;

    @Column(name = "compte_client_mutuelle", length = 20, nullable = false)
    private String compteClientMutuelle;

    @Column(name = "compte_client_autre", length = 20, nullable = false)
    private String compteClientAutre;

    @Column(name = "compte_banque", length = 20, nullable = false)
    private String compteBanque;

    @Column(name = "compte_caisse", length = 20, nullable = false)
    private String compteCaisse;

    @Column(name = "compte_tva_collectee", length = 20)
    private String compteTVACollectee;

    // ─── Stock (inventaire permanent) — vide = compte par défaut ─────────────

    @Column(name = "compte_stock", length = 20)
    private String compteStock;

    @Column(name = "compte_consommation", length = 20)
    private String compteConsommation;

    @Column(name = "compte_factures_non_parvenues", length = 20)
    private String compteFacturesNonParvenues;

    @Column(name = "compte_boni_inventaire", length = 20)
    private String compteBoniInventaire;

    @Column(name = "compte_mali_inventaire", length = 20)
    private String compteMaliInventaire;

    // ─── Journal de chaque opération — vide = journal par défaut ─────────────

    @Column(name = "journal_vente", length = 10)
    private String journalVente;

    @Column(name = "journal_reglement_banque", length = 10)
    private String journalReglementBanque;

    @Column(name = "journal_reglement_caisse", length = 10)
    private String journalReglementCaisse;

    @Column(name = "journal_stock_reception", length = 10)
    private String journalStockReception;

    @Column(name = "journal_stock_sortie", length = 10)
    private String journalStockSortie;

    @Column(name = "journal_stock_inventaire", length = 10)
    private String journalStockInventaire;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @PrePersist
    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // ─── Getters / Setters ───────────────────────────────────────────────────

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

    public String getCompteVentes() {
        return compteVentes;
    }

    public void setCompteVentes(String compteVentes) {
        this.compteVentes = compteVentes;
    }

    public String getCompteClientPatient() {
        return compteClientPatient;
    }

    public void setCompteClientPatient(String v) {
        this.compteClientPatient = v;
    }

    public String getCompteClientCnas() {
        return compteClientCnas;
    }

    public void setCompteClientCnas(String v) {
        this.compteClientCnas = v;
    }

    public String getCompteClientCasnos() {
        return compteClientCasnos;
    }

    public void setCompteClientCasnos(String v) {
        this.compteClientCasnos = v;
    }

    public String getCompteClientMutuelle() {
        return compteClientMutuelle;
    }

    public void setCompteClientMutuelle(String v) {
        this.compteClientMutuelle = v;
    }

    public String getCompteClientAutre() {
        return compteClientAutre;
    }

    public void setCompteClientAutre(String v) {
        this.compteClientAutre = v;
    }

    public String getCompteBanque() {
        return compteBanque;
    }

    public void setCompteBanque(String v) {
        this.compteBanque = v;
    }

    public String getCompteCaisse() {
        return compteCaisse;
    }

    public void setCompteCaisse(String v) {
        this.compteCaisse = v;
    }

    public String getCompteTVACollectee() {
        return compteTVACollectee;
    }

    public void setCompteTVACollectee(String v) {
        this.compteTVACollectee = v;
    }

    public String getCompteStock() {
        return compteStock;
    }

    public void setCompteStock(String v) {
        this.compteStock = v;
    }

    public String getCompteConsommation() {
        return compteConsommation;
    }

    public void setCompteConsommation(String v) {
        this.compteConsommation = v;
    }

    public String getCompteFacturesNonParvenues() {
        return compteFacturesNonParvenues;
    }

    public void setCompteFacturesNonParvenues(String v) {
        this.compteFacturesNonParvenues = v;
    }

    public String getCompteBoniInventaire() {
        return compteBoniInventaire;
    }

    public void setCompteBoniInventaire(String v) {
        this.compteBoniInventaire = v;
    }

    public String getCompteMaliInventaire() {
        return compteMaliInventaire;
    }

    public void setCompteMaliInventaire(String v) {
        this.compteMaliInventaire = v;
    }

    public String getJournalVente() {
        return journalVente;
    }

    public void setJournalVente(String v) {
        this.journalVente = v;
    }

    public String getJournalReglementBanque() {
        return journalReglementBanque;
    }

    public void setJournalReglementBanque(String v) {
        this.journalReglementBanque = v;
    }

    public String getJournalReglementCaisse() {
        return journalReglementCaisse;
    }

    public void setJournalReglementCaisse(String v) {
        this.journalReglementCaisse = v;
    }

    public String getJournalStockReception() {
        return journalStockReception;
    }

    public void setJournalStockReception(String v) {
        this.journalStockReception = v;
    }

    public String getJournalStockSortie() {
        return journalStockSortie;
    }

    public void setJournalStockSortie(String v) {
        this.journalStockSortie = v;
    }

    public String getJournalStockInventaire() {
        return journalStockInventaire;
    }

    public void setJournalStockInventaire(String v) {
        this.journalStockInventaire = v;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}

