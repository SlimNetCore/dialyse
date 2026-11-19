package com.hemodialyse.backend.contexts.stock.interfaces.http.pages;

import com.hemodialyse.backend.contexts.stock.domain.commandes.BonDeCommande;
import com.hemodialyse.backend.contexts.stock.domain.reception.BonDeReception;
import com.hemodialyse.backend.contexts.stock.domain.sortie.BonDeSortie;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Éléments des pages de bons : même forme JSON que les éléments des listes complètes ({@code BonsDeCommandeJson},
 * {@code ReceptionsJson}, {@code SortiesJson} du paquet parent, non visibles d'ici).
 */
final class BonsPagesJson {

    private BonsPagesJson() {
    }

    record LigneDeCommandeReponse(UUID id, UUID articleId, BigDecimal quantite, BigDecimal prixUnitaire) {
    }

    record BonDeCommandeReponse(UUID id, UUID centerId, String reference, UUID fournisseurId, String statut, String createdBy,
                                OffsetDateTime createdAt, List<LigneDeCommandeReponse> lignes) {
        static BonDeCommandeReponse depuis(BonDeCommande b) {
            return new BonDeCommandeReponse(b.id(), b.centre().valeur(), b.reference(), b.fournisseurId(), b.statut().name(),
                    b.creePar(), b.creeLe(), b.lignes().stream()
                    .map(l -> new LigneDeCommandeReponse(l.id(), l.articleId(), l.quantite(), l.prixUnitaire())).toList());
        }
    }

    record LigneDeReceptionReponse(UUID id, UUID articleId, BigDecimal quantite, BigDecimal prixUnitaire, String numeroLot,
                                   LocalDate datePeremption, UUID emplacementId, UUID lotId) {
    }

    record BonDeReceptionReponse(UUID id, UUID centerId, String reference, UUID bonCommandeId, UUID fournisseurId,
                                 LocalDate dateReception, String statut, String createdBy, OffsetDateTime createdAt,
                                 List<LigneDeReceptionReponse> lignes) {
        static BonDeReceptionReponse depuis(BonDeReception b) {
            return new BonDeReceptionReponse(b.id(), b.centre().valeur(), b.reference(), b.bonCommandeId(), b.fournisseurId(),
                    b.dateReception(), b.statut().name(), b.creePar(), b.creeLe(), b.lignes().stream()
                    .map(l -> new LigneDeReceptionReponse(l.id(), l.articleId(), l.quantite(), l.prixUnitaire(), l.numeroLot(),
                            l.datePeremption(), l.emplacementId(), l.lotId())).toList());
        }
    }

    record LigneDeSortieReponse(UUID id, UUID articleId, UUID lotId, BigDecimal quantite, BigDecimal pmpApplique) {
    }

    record BonDeSortieReponse(UUID id, UUID centerId, String reference, UUID seanceId, UUID patientId, String poste,
                              LocalDate dateSortie, String createdBy, OffsetDateTime createdAt, List<LigneDeSortieReponse> lignes) {
        static BonDeSortieReponse depuis(BonDeSortie b) {
            return new BonDeSortieReponse(b.id(), b.centre().valeur(), b.reference(), b.seanceId(), b.patientId(), b.poste(),
                    b.dateSortie(), b.creePar(), b.creeLe(), b.lignes().stream()
                    .map(l -> new LigneDeSortieReponse(l.id(), l.articleId(), l.lotId(), l.quantite(), l.pmpApplique())).toList());
        }
    }
}
