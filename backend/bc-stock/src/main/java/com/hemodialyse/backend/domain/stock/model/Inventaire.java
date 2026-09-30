package com.hemodialyse.backend.domain.stock.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Aggregate root — inventaire de stock d'un centre à une date.
 * <ul>
 *   <li>À l'ouverture, le stock théorique de chaque lot est figé ; tant que l'inventaire est EN_COURS, aucun
 *       mouvement de stock n'est permis dans le centre.</li>
 *   <li>À la clôture, chaque ligne doit être comptée et tout écart justifié ; les quantités comptées deviennent le
 *       stock de départ et les mouvements antérieurs sont clôturés (ignorés par les recalculs).</li>
 * </ul>
 */
public class Inventaire {

    private UUID id;
    private UUID centerId;
    private String reference;
    private LocalDate dateInventaire;
    private InventaireStatut statut;
    private String commentaire;
    private String createdBy;
    private OffsetDateTime createdAt;
    private String closedBy;
    private OffsetDateTime closedAt;
    private List<LigneInventaire> lignes = new ArrayList<>();

    public static Inventaire ouvrir(UUID centerId, String reference, LocalDate dateInventaire, String commentaire,
                                    String by, OffsetDateTime now, List<LigneInventaire> lignes) {
        if (dateInventaire == null) {
            throw new BusinessException("INVENTORY_DATE_REQUIRED", "La date d'inventaire est obligatoire.");
        }
        if (dateInventaire.isAfter(now.toLocalDate())) {
            throw new BusinessException("INVENTORY_DATE_IN_FUTURE", "La date d'inventaire ne peut pas être dans le futur.");
        }
        Inventaire inv = new Inventaire();
        inv.id = UUID.randomUUID();
        inv.centerId = centerId;
        inv.reference = reference;
        inv.dateInventaire = dateInventaire;
        inv.statut = InventaireStatut.EN_COURS;
        inv.commentaire = commentaire == null || commentaire.isBlank() ? null : commentaire.trim();
        inv.createdBy = by;
        inv.createdAt = now;
        inv.lignes = new ArrayList<>(lignes);
        return inv;
    }

    public static OffsetDateTime coupure(LocalDate date) {
        return date.atTime(LocalTime.of(23, 59, 59)).atOffset(ZoneOffset.UTC);
    }

    public static Inventaire restore(UUID id, UUID centerId, String reference, LocalDate dateInventaire,
                                     InventaireStatut statut, String commentaire, String createdBy,
                                     OffsetDateTime createdAt, String closedBy, OffsetDateTime closedAt,
                                     List<LigneInventaire> lignes) {
        Inventaire inv = new Inventaire();
        inv.id = id;
        inv.centerId = centerId;
        inv.reference = reference;
        inv.dateInventaire = dateInventaire;
        inv.statut = statut;
        inv.commentaire = commentaire;
        inv.createdBy = createdBy;
        inv.createdAt = createdAt;
        inv.closedBy = closedBy;
        inv.closedAt = closedAt;
        inv.lignes = new ArrayList<>(lignes);
        return inv;
    }

    /**
     * Fin du jour d'inventaire : tout mouvement daté jusqu'à cet instant est clôturé.
     */
    public OffsetDateTime coupure() {
        return coupure(dateInventaire);
    }

    public void ensureEnCours() {
        if (statut != InventaireStatut.EN_COURS) {
            throw new BusinessException("INVENTORY_NOT_IN_PROGRESS",
                    "L'inventaire " + reference + " est " + (statut == InventaireStatut.CLOTURE ? "clôturé" : "annulé")
                            + " : il n'est plus modifiable.");
        }
    }

    public LigneInventaire ligne(UUID ligneId) {
        return lignes.stream().filter(l -> l.getId().equals(ligneId)).findFirst()
                .orElseThrow(() -> new BusinessException("INVENTORY_LINE_NOT_FOUND", "Ligne d'inventaire introuvable."));
    }

    public void compter(UUID ligneId, BigDecimal quantite, String motif, String by, OffsetDateTime now) {
        ensureEnCours();
        ligne(ligneId).compter(quantite, motif, by, now);
    }

    /**
     * Lot trouvé physiquement mais absent du stock théorique (théorique = 0).
     */
    public LigneInventaire ajouterLigne(LigneInventaire ligne) {
        ensureEnCours();
        String numero = ligne.getNumeroLot() == null ? "" : ligne.getNumeroLot().trim().toUpperCase(Locale.ROOT);
        boolean doublon = lignes.stream().anyMatch(l -> l.getArticleId().equals(ligne.getArticleId())
                && numero.equals(l.getNumeroLot() == null ? "" : l.getNumeroLot().trim().toUpperCase(Locale.ROOT)));
        if (doublon) {
            throw new BusinessException("INVENTORY_LINE_DUPLICATE",
                    "Ce lot figure déjà dans l'inventaire : saisissez son comptage sur la ligne existante.");
        }
        ligne.setAjoutee(true);
        lignes.add(ligne);
        return ligne;
    }

    public void retirerLigne(UUID ligneId) {
        ensureEnCours();
        LigneInventaire ligne = ligne(ligneId);
        if (!ligne.isAjoutee()) {
            throw new BusinessException("INVENTORY_LINE_NOT_REMOVABLE",
                    "Seules les lignes ajoutées pendant le comptage peuvent être retirées (comptez 0 pour un lot absent).");
        }
        lignes.remove(ligne);
    }

    /**
     * Reporte la quantité théorique sur les lignes non encore comptées.
     */
    public int reporterTheorique(String by, OffsetDateTime now) {
        ensureEnCours();
        int count = 0;
        for (LigneInventaire l : lignes) {
            if (!l.isComptee()) {
                l.compter(l.getQuantiteTheorique(), null, by, now);
                count++;
            }
        }
        return count;
    }

    public long lignesNonComptees() {
        return lignes.stream().filter(l -> !l.isComptee()).count();
    }

    public long ecartsSansMotif() {
        return lignes.stream().filter(l -> l.hasEcart() && l.getMotifEcart() == null).count();
    }

    /**
     * Valide la clôture : toutes les lignes comptées et tous les écarts justifiés.
     */
    public void cloturer(String by, OffsetDateTime now) {
        ensureEnCours();
        long nonComptees = lignesNonComptees();
        if (nonComptees > 0) {
            throw new BusinessException("INVENTORY_NOT_COMPLETE",
                    nonComptees + " ligne(s) ne sont pas encore comptées : comptez-les (0 si le lot est absent) avant de clôturer.");
        }
        long sansMotif = ecartsSansMotif();
        if (sansMotif > 0) {
            throw new BusinessException("INVENTORY_GAP_WITHOUT_REASON",
                    sansMotif + " écart(s) sans motif : justifiez chaque écart avant de clôturer.");
        }
        statut = InventaireStatut.CLOTURE;
        closedBy = by;
        closedAt = now;
    }

    public void annuler(String by, OffsetDateTime now) {
        ensureEnCours();
        statut = InventaireStatut.ANNULE;
        closedBy = by;
        closedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public String getReference() {
        return reference;
    }

    public LocalDate getDateInventaire() {
        return dateInventaire;
    }

    public InventaireStatut getStatut() {
        return statut;
    }

    public String getCommentaire() {
        return commentaire;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getClosedBy() {
        return closedBy;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public List<LigneInventaire> getLignes() {
        return lignes;
    }
}

