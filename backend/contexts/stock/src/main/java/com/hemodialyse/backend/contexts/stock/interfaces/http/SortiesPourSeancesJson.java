package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.application.sortieseances.GestionDesSortiesPourSeances.Demande;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.DemiJournee;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.LigneDePiece;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.PerimetreDeSortie;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiePourSeances;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * JSON des sorties pour séances (slice S6b, nouvelle fonctionnalité).
 */
final class SortiesPourSeancesJson {

    private SortiesPourSeancesJson() {
    }

    record LigneRequete(UUID articleId, BigDecimal quantite) {
        Demande demande() {
            return new Demande(articleId, quantite);
        }
    }

    record PieceRequete(UUID centerId, LocalDate jour, String salle, DemiJournee demiJournee,
                        List<LigneRequete> lignes) {
        PerimetreDeSortie perimetre() {
            return new PerimetreDeSortie(salle, demiJournee);
        }

        List<Demande> demandes() {
            return lignes == null ? List.of() : lignes.stream().map(LigneRequete::demande).toList();
        }
    }

    record MotifRequete(UUID centerId, String motif) {
    }

    record CentreRequete(UUID centerId) {
    }

    record LigneReponse(UUID id, UUID articleId, UUID lotId, BigDecimal quantiteSortie, BigDecimal valeurUnitaire,
                        BigDecimal quantiteConsommee, BigDecimal ecart, String motif) {
        static LigneReponse depuis(LigneDePiece l) {
            return new LigneReponse(l.id(), l.articleId(), l.lotId(), l.quantiteSortie(), l.valeurUnitaire(), l.quantiteConsommee(),
                    l.ecart(), l.motif());
        }
    }

    record PieceReponse(UUID id, UUID centerId, String reference, LocalDate jour, String salle, DemiJournee demiJournee,
                        String statut,
                        OffsetDateTime enregistreeLe, String enregistreePar, OffsetDateTime valideeLe,
                        String valideePar,
                        List<LigneReponse> lignes) {
        static PieceReponse depuis(SortiePourSeances p) {
            return new PieceReponse(p.id(), p.centre().valeur(), p.reference(), p.jour(), p.perimetre().salle(),
                    p.perimetre().demiJournee(), p.statut().name(), p.enregistreeLe(), p.enregistreePar(), p.valideeLe(),
                    p.valideePar(), p.lignes().stream().map(LigneReponse::depuis).toList());
        }
    }
}

