package com.hemodialyse.backend.domain.comptabilite.port;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.aggregate.ModelePiece;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port entrant — modèles de pièces d'un centre et saisie de pièces à partir de ces modèles.
 */
public interface PiecesComptablesUseCase {

    PagedResult<ModelePiece> listerModeles(UUID centerId, boolean actifsSeulement, int page, int size);

    /**
     * Crée le modèle ou remplace celui qui porte cet identifiant.
     */
    ModelePiece enregistrerModele(ModelePiece modele);

    void supprimerModele(UUID centerId, UUID modeleId);

    /**
     * Saisit une pièce : une écriture validée dans le journal du modèle.
     */
    EcritureComptable saisir(SaisirPieceCommand commande);

    /**
     * Annule une pièce saisie par une écriture inverse (une écriture ne se supprime jamais).
     *
     * @param aujourdhui date de l'extourne
     */
    EcritureComptable extourner(UUID centerId, UUID ecritureId, LocalDate aujourdhui);

    /**
     * @param montants un montant par ligne du modèle, dans l'ordre ; zéro = ligne non utilisée dans cette pièce
     */
    record SaisirPieceCommand(UUID centerId, UUID modeleId, LocalDate date, String libelle, List<BigDecimal> montants) {
        public SaisirPieceCommand {
            if (centerId == null || modeleId == null) {
                throw new IllegalArgumentException("Le centre et le modèle sont obligatoires");
            }
            if (date == null) throw new IllegalArgumentException("La date de la pièce est obligatoire");
            montants = montants == null ? List.of() : montants.stream()
                    .map(m -> m == null ? BigDecimal.ZERO : m).toList();
        }
    }
}
