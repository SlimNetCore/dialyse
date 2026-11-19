package com.hemodialyse.backend.contexts.stock.application.consultation.seances;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.DemiJournee;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.LigneDePiece;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiePourSeances;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Port de lecture paginée des sorties pour séances (RG-STK-091) : pièces du centre sans leurs lignes (avec leur nombre), puis
 * lignes d'une pièce avec le code et le libellé de l'article. Pagination, tri et filtres exécutés par l'adapter (SQL).
 */
public interface PagesDesSortiesPourSeances {

    PageDeResultats<ResumeDePiece> pieces(CentreId centre, FiltresDesPieces filtres, DemandeDePage demande);

    /** La pièce existe-t-elle dans ce centre ? (isolation : une pièce d'un autre centre est inconnue) */
    boolean existe(CentreId centre, UUID piece);

    /** @param article texte normalisé cherché dans le code OU le libellé de l'article */
    PageDeResultats<LigneDetaillee> lignes(CentreId centre, UUID piece, Optional<String> article, DemandeDePage demande);

    record FiltresDesPieces(Optional<String> reference, Set<SortiePourSeances.Statut> statuts) {
    }

    /** Une pièce sans ses lignes, avec leur nombre. */
    record ResumeDePiece(UUID id, CentreId centre, String reference, LocalDate jour, String salle, DemiJournee demiJournee,
                         SortiePourSeances.Statut statut, OffsetDateTime enregistreeLe, String enregistreePar,
                         OffsetDateTime valideeLe, String valideePar, int nombreDeLignes) {
    }

    /** Une ligne de pièce avec l'article lisible (code, libellé). */
    record LigneDetaillee(LigneDePiece ligne, String articleCode, String articleLibelle) {
    }
}
