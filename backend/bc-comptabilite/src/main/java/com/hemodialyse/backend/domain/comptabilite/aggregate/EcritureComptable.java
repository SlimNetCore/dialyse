package com.hemodialyse.backend.domain.comptabilite.aggregate;

import com.hemodialyse.backend.domain.comptabilite.entity.LigneEcriture;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.StatutEcriture;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Agrégat racine du bounded context comptabilité.
 * <p>
 * Invariants portés dans le constructeur (pas en validation applicative) :
 * — Σdébit = Σcrédit sur l'ensemble des lignes.
 * — Une écriture EXPORTEE est immuable ; toute correction passe par une extourne.
 * — Numéro de pièce : séquentiel par journal, sans trou, jamais réutilisé (géré par le repository).
 * <p>
 * Référence d'idempotence : {@code sourceId} est l'identifiant de l'opération source
 * (factureId, paiementId…). Le repository garantit l'unicité (centerId, journalCode, sourceId).
 */
public class EcritureComptable {

    private final UUID id;
    private final UUID centerId;
    private final JournalCode journalCode;
    private final LocalDate dateEcriture;
    private final LocalDate datePiece;
    private final String numeroPiece;
    private final String libelle;
    private final List<LigneEcriture> lignes;
    /**
     * Identifiant de l'opération source — garantit l'idempotence.
     */
    private final UUID sourceId;
    private StatutEcriture statut;

    public EcritureComptable(UUID id, UUID centerId, JournalCode journalCode,
                             LocalDate dateEcriture, LocalDate datePiece,
                             String numeroPiece, String libelle,
                             List<LigneEcriture> lignes, StatutEcriture statut,
                             UUID sourceId) {
        if (id == null || centerId == null) {
            throw new IllegalArgumentException("L'identifiant et le centerId sont obligatoires");
        }
        if (journalCode == null) throw new IllegalArgumentException("Le code journal est obligatoire");
        if (dateEcriture == null || datePiece == null)
            throw new IllegalArgumentException("Les dates sont obligatoires");
        if (numeroPiece == null || numeroPiece.isBlank())
            throw new IllegalArgumentException("Le numéro de pièce est obligatoire");
        if (lignes == null || lignes.size() < 2) {
            throw new IllegalArgumentException("Une écriture doit contenir au moins 2 lignes");
        }
        // Invariant SCF : équilibre débit/crédit
        BigDecimal totalDebit = lignes.stream().map(LigneEcriture::getMontantDebit).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCredit = lignes.stream().map(LigneEcriture::getMontantCredit).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new BusinessException(
                    "ECRITURE_DESEQUILIBREE",
                    "L'écriture est déséquilibrée : débit=" + totalDebit + " crédit=" + totalCredit
            );
        }
        this.id = id;
        this.centerId = centerId;
        this.journalCode = journalCode;
        this.dateEcriture = dateEcriture;
        this.datePiece = datePiece;
        this.numeroPiece = numeroPiece;
        this.libelle = libelle != null ? libelle.trim() : "";
        this.lignes = List.copyOf(lignes);
        this.statut = statut != null ? statut : StatutEcriture.BROUILLON;
        this.sourceId = sourceId;
    }

    /**
     * Valide l'écriture (passe de BROUILLON à VALIDEE).
     * Une écriture EXPORTEE ne peut pas être revalidée.
     */
    public void valider() {
        if (statut == StatutEcriture.EXPORTEE) {
            throw new BusinessException("ECRITURE_EXPORTEE_IMMUABLE",
                    "Une écriture exportée ne peut pas être modifiée");
        }
        this.statut = StatutEcriture.VALIDEE;
    }

    /**
     * Marque l'écriture comme exportée (immuable ensuite).
     */
    public void marquerExportee() {
        if (statut != StatutEcriture.VALIDEE) {
            throw new BusinessException("EXPORT_ECRITURE_NON_VALIDEE",
                    "Seules les écritures validées peuvent être exportées");
        }
        this.statut = StatutEcriture.EXPORTEE;
    }

    public BigDecimal totalDebit() {
        return lignes.stream().map(LigneEcriture::getMontantDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    // ─── Accesseurs ──────────────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public JournalCode getJournalCode() {
        return journalCode;
    }

    public LocalDate getDateEcriture() {
        return dateEcriture;
    }

    public LocalDate getDatePiece() {
        return datePiece;
    }

    public String getNumeroPiece() {
        return numeroPiece;
    }

    public String getLibelle() {
        return libelle;
    }

    public List<LigneEcriture> getLignes() {
        return lignes;
    }

    public StatutEcriture getStatut() {
        return statut;
    }

    public UUID getSourceId() {
        return sourceId;
    }
}

