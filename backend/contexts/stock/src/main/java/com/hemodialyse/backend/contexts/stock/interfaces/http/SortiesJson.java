package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.application.sortie.GestionDesSorties.LigneSaisie;
import com.hemodialyse.backend.contexts.stock.application.sortie.GestionDesSorties.LotDisponible;
import com.hemodialyse.backend.contexts.stock.domain.sortie.BonDeSortie;
import com.hemodialyse.backend.contexts.stock.domain.sortie.LigneDeSortie;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * JSON des bons de sortie, identique au legacy ({@code BonSortie}, {@code LigneSortie}) : mêmes champs ; en écriture le centre
 * est facultatif et l'auteur du corps est ignoré (session).
 */
final class SortiesJson {

    private SortiesJson() {
    }

    record ItemRequete(UUID articleId, UUID lotId, BigDecimal quantite) {
        LigneSaisie saisie() {
            return new LigneSaisie(articleId, lotId, quantite);
        }
    }

    record BonRequete(UUID centerId, UUID seanceId, UUID patientId, String poste, LocalDate dateSortie, String userId,
                      List<ItemRequete> items) {
        List<LigneSaisie> saisies() {
            return items == null ? null : items.stream().map(ItemRequete::saisie).toList();
        }
    }

    record LigneReponse(UUID id, UUID articleId, UUID lotId, BigDecimal quantite, BigDecimal pmpApplique) {
        static LigneReponse depuis(LigneDeSortie l) {
            return new LigneReponse(l.id(), l.articleId(), l.lotId(), l.quantite(), l.pmpApplique());
        }
    }

    record BonReponse(UUID id, UUID centerId, String reference, UUID seanceId, UUID patientId, String poste,
                      LocalDate dateSortie,
                      String createdBy, OffsetDateTime createdAt, List<LigneReponse> lignes) {
        static BonReponse depuis(BonDeSortie b) {
            return new BonReponse(b.id(), b.centre().valeur(), b.reference(), b.seanceId(), b.patientId(), b.poste(),
                    b.dateSortie(), b.creePar(), b.creeLe(), b.lignes().stream().map(LigneReponse::depuis).toList());
        }
    }

    record LotDisponibleReponse(UUID id, String numeroLot, LocalDate datePeremption, BigDecimal pmp,
                                BigDecimal quantiteRestante) {
        static LotDisponibleReponse depuis(LotDisponible l) {
            return new LotDisponibleReponse(l.id(), l.numeroLot(), l.datePeremption(), l.pmp(), l.quantiteRestante());
        }
    }
}

