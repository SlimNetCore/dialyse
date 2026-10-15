package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Modèle de persistance du mapping comptable : table {@code mapping_comptable} (V32 et antérieures), avec les mêmes noms de
 * colonnes, longueurs et contrainte d'unicité ({@code uq_mapping_center}, une ligne par centre). La colonne
 * {@code compte_client_autre} porte le compte client par défaut (nom historique). Jamais exposé hors de
 * {@code infrastructure.persistence}.
 */
@Entity
@Table(name = "mapping_comptable", uniqueConstraints = @UniqueConstraint(name = "uq_mapping_center", columnNames = "center_id"))
public class MappingComptableJpa {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "center_id", nullable = false, columnDefinition = "uuid")
    private UUID centerId;

    @Column(name = "compte_ventes", length = 20, nullable = false)
    private String compteVentes;

    @Column(name = "compte_client_patient", length = 20, nullable = false)
    private String compteClientPatient;

    @Column(name = "compte_client_autre", length = 20, nullable = false)
    private String compteClientDefaut;

    @Column(name = "compte_banque", length = 20, nullable = false)
    private String compteBanque;

    @Column(name = "compte_caisse", length = 20, nullable = false)
    private String compteCaisse;

    @Column(name = "compte_tva_collectee", length = 20)
    private String compteTvaCollectee;

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

    protected MappingComptableJpa() {
    }

    MappingComptableJpa(UUID id, UUID centerId) {
        this.id = id;
        this.centerId = centerId;
    }

    @PrePersist
    @PreUpdate
    void horodater() {
        updatedAt = OffsetDateTime.now();
    }

    UUID getCenterId() {
        return centerId;
    }

    String getCompteVentes() {
        return compteVentes;
    }

    void setCompteVentes(String v) {
        this.compteVentes = v;
    }

    String getCompteClientPatient() {
        return compteClientPatient;
    }

    void setCompteClientPatient(String v) {
        this.compteClientPatient = v;
    }

    String getCompteClientDefaut() {
        return compteClientDefaut;
    }

    void setCompteClientDefaut(String v) {
        this.compteClientDefaut = v;
    }

    String getCompteBanque() {
        return compteBanque;
    }

    void setCompteBanque(String v) {
        this.compteBanque = v;
    }

    String getCompteCaisse() {
        return compteCaisse;
    }

    void setCompteCaisse(String v) {
        this.compteCaisse = v;
    }

    String getCompteTvaCollectee() {
        return compteTvaCollectee;
    }

    void setCompteTvaCollectee(String v) {
        this.compteTvaCollectee = v;
    }

    String getCompteStock() {
        return compteStock;
    }

    void setCompteStock(String v) {
        this.compteStock = v;
    }

    String getCompteConsommation() {
        return compteConsommation;
    }

    void setCompteConsommation(String v) {
        this.compteConsommation = v;
    }

    String getCompteFacturesNonParvenues() {
        return compteFacturesNonParvenues;
    }

    void setCompteFacturesNonParvenues(String v) {
        this.compteFacturesNonParvenues = v;
    }

    String getCompteBoniInventaire() {
        return compteBoniInventaire;
    }

    void setCompteBoniInventaire(String v) {
        this.compteBoniInventaire = v;
    }

    String getCompteMaliInventaire() {
        return compteMaliInventaire;
    }

    void setCompteMaliInventaire(String v) {
        this.compteMaliInventaire = v;
    }

    String getJournalVente() {
        return journalVente;
    }

    void setJournalVente(String v) {
        this.journalVente = v;
    }

    String getJournalReglementBanque() {
        return journalReglementBanque;
    }

    void setJournalReglementBanque(String v) {
        this.journalReglementBanque = v;
    }

    String getJournalReglementCaisse() {
        return journalReglementCaisse;
    }

    void setJournalReglementCaisse(String v) {
        this.journalReglementCaisse = v;
    }

    String getJournalStockReception() {
        return journalStockReception;
    }

    void setJournalStockReception(String v) {
        this.journalStockReception = v;
    }

    String getJournalStockSortie() {
        return journalStockSortie;
    }

    void setJournalStockSortie(String v) {
        this.journalStockSortie = v;
    }

    String getJournalStockInventaire() {
        return journalStockInventaire;
    }

    void setJournalStockInventaire(String v) {
        this.journalStockInventaire = v;
    }
}
