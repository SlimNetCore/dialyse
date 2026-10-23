package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.application.reception.GestionDesReceptions.LigneSaisie;
import com.hemodialyse.backend.contexts.stock.domain.reception.BonDeReception;
import com.hemodialyse.backend.contexts.stock.domain.reception.LigneDeReception;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * JSON des bons de réception, identique au legacy ({@code BonReception}, {@code LigneReception}) : mêmes champs ; en écriture
 * le centre et l'auteur du corps sont facultatifs et l'auteur est ignoré (session).
 */
final class ReceptionsJson {

    private ReceptionsJson() {
    }

    record LigneRequete(UUID id, UUID articleId, BigDecimal quantite, BigDecimal prixUnitaire, String numeroLot,
                        LocalDate datePeremption, UUID emplacementId) {
        LigneSaisie saisie() {
            return new LigneSaisie(id, articleId, quantite, prixUnitaire, numeroLot, datePeremption, emplacementId);
        }
    }

    record BonRequete(UUID centerId, UUID bonCommandeId, UUID fournisseurId, LocalDate dateReception, String userId,
                      List<LigneRequete> lignes) {
        List<LigneSaisie> saisies() {
            return lignes == null ? List.of() : lignes.stream().map(LigneRequete::saisie).toList();
        }
    }

    record DepuisBonDeCommandeRequete(UUID centerId, UUID bonCommandeId, String userId) {
    }

    record CentreRequete(UUID centerId) {
    }

    record LigneReponse(UUID id, UUID articleId, BigDecimal quantite, BigDecimal prixUnitaire, String numeroLot,
                        LocalDate datePeremption, UUID emplacementId, UUID lotId) {
        static LigneReponse depuis(LigneDeReception l) {
            return new LigneReponse(l.id(), l.articleId(), l.quantite(), l.prixUnitaire(), l.numeroLot(), l.datePeremption(),
                    l.emplacementId(), l.lotId());
        }
    }

    record BonReponse(UUID id, UUID centerId, String reference, UUID bonCommandeId, UUID fournisseurId,
                      LocalDate dateReception,
                      String statut, String createdBy, OffsetDateTime createdAt, List<LigneReponse> lignes) {
        static BonReponse depuis(BonDeReception b) {
            return new BonReponse(b.id(), b.centre().valeur(), b.reference(), b.bonCommandeId(), b.fournisseurId(),
                    b.dateReception(), b.statut().name(), b.creePar(), b.creeLe(),
                    b.lignes().stream().map(LigneReponse::depuis).toList());
        }
    }
}

