package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.application.inventaire.GestionDesInventaires;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaire;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaires;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.LigneDInventaire;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.ResultatDImport;
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
 * JSON de l'API d'inventaire de stock (S7) : mêmes champs que le legacy.
 */
final class InventairesJson {

    private InventairesJson() {
    }

    record LigneReponse(UUID id, UUID articleId, String articleCode, String articleLibelle, String unite, UUID lotId,
                        String numeroLot,
                        LocalDate datePeremption, BigDecimal quantiteTheorique, BigDecimal quantiteComptee,
                        BigDecimal ecart,
                        BigDecimal pmp, BigDecimal valeurEcart, String motifEcart, String comptePar,
                        OffsetDateTime compteLe,
                        boolean ajoutee) {
        static LigneReponse depuis(LigneDInventaire l) {
            return new LigneReponse(l.id(), l.articleId(), l.articleCode(), l.articleLibelle(), l.unite(), l.lotId(), l.numeroLot(),
                    l.datePeremption(), l.quantiteTheorique(), l.quantiteComptee(), l.ecart(), l.pmp(), l.valeurEcart(), l.motifEcart(),
                    l.comptePar(), l.compteLe(), l.ajoutee());
        }
    }

    /**
     * Inventaire complet avec indicateurs de progression et de valorisation des écarts.
     */
    record InventaireReponse(UUID id, String reference, LocalDate dateInventaire, String statut, String commentaire,
                             String createdBy,
                             OffsetDateTime createdAt, String closedBy, OffsetDateTime closedAt, int lignes,
                             int comptees, int ecarts,
                             int ecartsSansMotif, BigDecimal valeurTheorique, BigDecimal valeurComptee,
                             BigDecimal valeurEcarts,
                             List<LigneReponse> details) {
        static InventaireReponse depuis(Inventaire inv) {
            List<LigneDInventaire> l = inv.lignes();
            BigDecimal theorique = l.stream().map(x -> x.quantiteTheorique().multiply(x.pmp())).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal comptee = l.stream().filter(LigneDInventaire::estComptee).map(x -> x.quantiteComptee().multiply(x.pmp()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            BigDecimal ecarts = l.stream().filter(LigneDInventaire::estComptee).map(LigneDInventaire::valeurEcart)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            return new InventaireReponse(inv.id(), inv.reference(), inv.dateInventaire(), inv.statut().name(), inv.commentaire(),
                    inv.creePar(), inv.creeLe(), inv.clotureePar(), inv.clotureeLe(), l.size(),
                    (int) l.stream().filter(LigneDInventaire::estComptee).count(), (int) l.stream().filter(LigneDInventaire::aUnEcart).count(),
                    (int) inv.ecartsSansMotif(), theorique, comptee, ecarts, l.stream().map(LigneReponse::depuis).toList());
        }
    }

    record ResumeReponse(UUID id, String reference, LocalDate dateInventaire, String statut, int lignes, int comptees,
                         int ecarts,
                         BigDecimal valeurEcarts, String createdBy, OffsetDateTime createdAt, String closedBy,
                         OffsetDateTime closedAt) {
        static ResumeReponse depuis(Inventaires.Resume r) {
            return new ResumeReponse(r.id(), r.reference(), r.dateInventaire(), r.statut().name(), r.lignes(), r.comptees(), r.ecarts(),
                    r.valeurEcarts(), r.creePar(), r.creeLe(), r.clotureePar(), r.clotureeLe());
        }
    }

    /**
     * Enveloppe de pagination de l'API ({@code items / total / page / size}).
     */
    record Page<T>(List<T> items, long total, int page, int size) {
    }

    /**
     * Situation du centre : inventaire en cours (mouvements gelés) et date du dernier inventaire clôturé.
     */
    record EtatReponse(boolean mouvementsBloques, UUID inventaireEnCoursId, String inventaireEnCoursReference,
                       LocalDate inventaireEnCoursDate, String ouvertPar, LocalDate derniereCloture) {
        static EtatReponse depuis(GestionDesInventaires.Etat etat) {
            Inventaire enCours = etat.enCours().orElse(null);
            return new EtatReponse(enCours != null, enCours != null ? enCours.id() : null, enCours != null ? enCours.reference() : null,
                    enCours != null ? enCours.dateInventaire() : null, enCours != null ? enCours.creePar() : null, etat.derniereCloture());
        }
    }

    record OuvrirRequete(UUID centerId, LocalDate dateInventaire, @Size(max = 1000) String commentaire) {
    }

    record ComptageRequete(UUID centerId, @NotNull BigDecimal quantite, @Size(max = 255) String motif) {
    }

    record AjoutLigneRequete(UUID centerId, @NotNull UUID articleId, @Size(max = 100) String numeroLot,
                             LocalDate datePeremption,
                             @NotNull BigDecimal quantite, @Size(max = 255) String motif) {
    }

    record AnomalieReponse(int ligne, String message) {
    }

    /**
     * Bilan de l'import d'une feuille de comptage remplie.
     */
    record ImportReponse(InventaireReponse inventaire, int lignesMisesAJour, int lignesInchangees, int lignesVides,
                         List<AnomalieReponse> anomalies) {
        static ImportReponse depuis(ResultatDImport r, List<ResultatDImport.Anomalie> lecture) {
            List<AnomalieReponse> anomalies = Stream.concat(lecture.stream(), r.anomalies().stream())
                    .sorted(Comparator.comparingInt(ResultatDImport.Anomalie::ligneFichier))
                    .map(a -> new AnomalieReponse(a.ligneFichier(), a.message())).toList();
            return new ImportReponse(InventaireReponse.depuis(r.inventaire()), r.misesAJour(), r.inchangees(), r.vides(), anomalies);
        }
    }
}

