package com.hemodialyse.backend.infrastructure.web.dto.stock;

import com.hemodialyse.backend.domain.stock.model.Inventaire;
import com.hemodialyse.backend.domain.stock.model.LigneInventaire;
import com.hemodialyse.backend.domain.stock.model.ResultatImportComptage;
import com.hemodialyse.backend.domain.stock.port.InventaireRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.InventaireUseCase;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * DTO de l'API d'inventaire de stock.
 */
public final class InventaireDtos {

    private InventaireDtos() {
    }

    public record LigneResponse(UUID id, UUID articleId, String articleCode, String articleLibelle, String unite,
                                UUID lotId, String numeroLot, LocalDate datePeremption, BigDecimal quantiteTheorique,
                                BigDecimal quantiteComptee, BigDecimal ecart, BigDecimal pmp, BigDecimal valeurEcart,
                                String motifEcart, String comptePar, OffsetDateTime compteLe, boolean ajoutee) {
        static LigneResponse from(LigneInventaire l) {
            return new LigneResponse(l.getId(), l.getArticleId(), l.getArticleCode(), l.getArticleLibelle(), l.getUnite(),
                    l.getLotId(), l.getNumeroLot(), l.getDatePeremption(), l.getQuantiteTheorique(), l.getQuantiteComptee(),
                    l.ecart(), l.getPmp(), l.valeurEcart(), l.getMotifEcart(), l.getComptePar(), l.getCompteLe(), l.isAjoutee());
        }
    }

    /**
     * Inventaire complet avec indicateurs de progression et de valorisation des écarts.
     */
    public record InventaireResponse(UUID id, String reference, LocalDate dateInventaire, String statut,
                                     String commentaire,
                                     String createdBy, OffsetDateTime createdAt, String closedBy,
                                     OffsetDateTime closedAt,
                                     int lignes, int comptees, int ecarts, int ecartsSansMotif,
                                     BigDecimal valeurTheorique,
                                     BigDecimal valeurComptee, BigDecimal valeurEcarts, List<LigneResponse> details) {
        public static InventaireResponse from(Inventaire inv) {
            List<LigneInventaire> l = inv.getLignes();
            BigDecimal theorique = l.stream().map(x -> x.getQuantiteTheorique().multiply(x.getPmp()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            BigDecimal comptee = l.stream().filter(LigneInventaire::isComptee)
                    .map(x -> x.getQuantiteComptee().multiply(x.getPmp()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            BigDecimal ecarts = l.stream().filter(LigneInventaire::isComptee).map(LigneInventaire::valeurEcart)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            return new InventaireResponse(inv.getId(), inv.getReference(), inv.getDateInventaire(), inv.getStatut().name(),
                    inv.getCommentaire(), inv.getCreatedBy(), inv.getCreatedAt(), inv.getClosedBy(), inv.getClosedAt(),
                    l.size(), (int) l.stream().filter(LigneInventaire::isComptee).count(),
                    (int) l.stream().filter(LigneInventaire::hasEcart).count(), (int) inv.ecartsSansMotif(),
                    theorique, comptee, ecarts, l.stream().map(LigneResponse::from).toList());
        }
    }

    public record InventaireResumeResponse(UUID id, String reference, LocalDate dateInventaire, String statut,
                                           int lignes,
                                           int comptees, int ecarts, BigDecimal valeurEcarts, String createdBy,
                                           OffsetDateTime createdAt, String closedBy, OffsetDateTime closedAt) {
        public static InventaireResumeResponse from(InventaireRepositoryPort.InventaireResume r) {
            return new InventaireResumeResponse(r.id(), r.reference(), r.dateInventaire(), r.statut().name(), r.lignes(),
                    r.comptees(), r.ecarts(), r.valeurEcarts(), r.createdBy(), r.createdAt(), r.closedBy(), r.closedAt());
        }
    }

    /**
     * Situation du centre : inventaire en cours (mouvements gelés) et date du dernier inventaire clôturé.
     */
    public record EtatResponse(boolean mouvementsBloques, UUID inventaireEnCoursId, String inventaireEnCoursReference,
                               LocalDate inventaireEnCoursDate, String ouvertPar, LocalDate derniereCloture) {
        public static EtatResponse from(InventaireUseCase.EtatInventaire etat) {
            Inventaire enCours = etat.enCours();
            return new EtatResponse(enCours != null, enCours != null ? enCours.getId() : null,
                    enCours != null ? enCours.getReference() : null, enCours != null ? enCours.getDateInventaire() : null,
                    enCours != null ? enCours.getCreatedBy() : null, etat.derniereCloture());
        }
    }

    public record OuvrirRequest(UUID centerId, LocalDate dateInventaire, @Size(max = 1000) String commentaire) {
    }

    public record ComptageRequest(UUID centerId, @NotNull BigDecimal quantite, @Size(max = 255) String motif) {
    }

    public record AjoutLigneRequest(UUID centerId, @NotNull UUID articleId, @Size(max = 100) String numeroLot,
                                    LocalDate datePeremption, @NotNull BigDecimal quantite,
                                    @Size(max = 255) String motif) {
    }

    public record AnomalieImportResponse(int ligne, String message) {
    }

    /**
     * Bilan de l'import d'une feuille de comptage remplie.
     */
    public record ImportComptageResponse(InventaireResponse inventaire, int lignesMisesAJour, int lignesInchangees,
                                         int lignesVides, List<AnomalieImportResponse> anomalies) {
        public static ImportComptageResponse from(ResultatImportComptage r, List<ResultatImportComptage.Anomalie> lecture) {
            List<AnomalieImportResponse> anomalies = Stream.concat(lecture.stream(), r.anomalies().stream())
                    .sorted(Comparator.comparingInt(ResultatImportComptage.Anomalie::ligneFichier))
                    .map(a -> new AnomalieImportResponse(a.ligneFichier(), a.message()))
                    .toList();
            return new ImportComptageResponse(InventaireResponse.from(r.inventaire()), r.lignesMisesAJour(),
                    r.lignesInchangees(), r.lignesVides(), anomalies);
        }
    }
}

